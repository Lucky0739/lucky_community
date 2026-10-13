package com.lucky.comment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.comment.dto.PublishCommentDTO;
import com.lucky.comment.enums.CommentLevelEnum;
import com.lucky.common.auth.UserContext;
import com.lucky.common.constant.CommentConstant;
import com.lucky.common.exception.BusinessException;
import com.lucky.comment.entity.Comment;
import com.lucky.comment.entity.PostRef;
import com.lucky.comment.mapper.CommentMapper;
import com.lucky.comment.mapper.PostReadMapper;
import com.lucky.comment.mq.CommentMqSender;
import com.lucky.comment.mq.PublishCommentMessage;
import com.lucky.comment.service.CommentPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentPublishServiceImpl implements CommentPublishService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PostReadMapper postReadMapper;
    private final CommentMapper commentMapper;
    private final CommentMqSender commentMqSender;
    private final Clock clock;

    @Override
    public Long publish(PublishCommentDTO publishCommentDTO) {
        // 获取登录用户id
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        // 查询帖子是否存在
        PostRef postRef = postReadMapper.selectById(publishCommentDTO.getPostId());
        if (postRef == null || postRef.getDeleted() == null || postRef.getDeleted() != 0) {
            throw new BusinessException("帖子不存在");
        }

        String content = publishCommentDTO.getContent() == null ? "" : publishCommentDTO.getContent().trim();
        String imageUrl = publishCommentDTO.getImageUrl() == null ? "" : publishCommentDTO.getImageUrl().trim();
        if (!StringUtils.hasText(content) && !StringUtils.hasText(imageUrl)) {
            throw new BusinessException("评论内容不能为空");
        }
        if (content.codePointCount(0, content.length()) > CommentConstant.MAX_CONTENT_LENGTH) {
            throw new BusinessException("评论不能超过500字");
        }
        // 组建消息发送类
        PublishCommentMessage message = new PublishCommentMessage();
        message.setId(IdUtil.getSnowflakeNextId());
        message.setPostId(postRef.getId());
        message.setBarId(postRef.getBarId());
        message.setUserId(userId);
        message.setImageUrl(imageUrl);
        message.setCreateTime(clock.millis());
        fillReply(message, publishCommentDTO.getReplyCommentId(), postRef.getId());
        fillContent(message, content);

        try {
            commentMqSender.send(message);
        } catch (Exception e) {
            throw new BusinessException("评论发送失败，请稍后重试");
        }
        return message.getId();
    }

    /**
     * 回复评论
     * @param publishCommentMessage
     * @param replyCommentId
     * @param postId
     */
    private void fillReply(PublishCommentMessage publishCommentMessage, Long replyCommentId, Long postId) {
        // 没有回复的帖子，单纯发表评论
        if (replyCommentId == null || replyCommentId == 0) {
            publishCommentMessage.setLevel(CommentLevelEnum.ONE.getCode());
            publishCommentMessage.setParentId(postId);
            publishCommentMessage.setReplyCommentId(CommentConstant.NONE);
            publishCommentMessage.setReplyUserId(CommentConstant.NONE);
            return;
        }
        // 查询回复评论
        Comment target = commentMapper.selectById(replyCommentId);
        if (target == null || target.getDeleted() == null || target.getDeleted() != 0
                || !postId.equals(target.getPostId())) {
            throw new BusinessException("要回复的评论不存在");
        }
        publishCommentMessage.setLevel(CommentLevelEnum.TWO.getCode());
        publishCommentMessage.setReplyCommentId(target.getId());
        publishCommentMessage.setReplyUserId(target.getUserId());
        if (CommentLevelEnum.ONE.getCode().equals(target.getLevel())) {
            publishCommentMessage.setParentId(target.getId());
        } else {
            publishCommentMessage.setParentId(target.getParentId());
        }
    }

    private void fillContent(PublishCommentMessage message, String content) {
        if (!StringUtils.hasText(content)) {
            message.setContent("");
            message.setContentUuid("");
            message.setContentEmpty(CommentConstant.CONTENT_EMPTY);
            message.setYearMonth(yearMonth(message.getCreateTime()));
            return;
        }
        message.setContent(content);
        message.setContentUuid(new UUID(0L, message.getId()).toString());
        message.setContentEmpty(CommentConstant.CONTENT_NOT_EMPTY);
        message.setYearMonth(yearMonth(message.getCreateTime()));
    }

    private String yearMonth(Long epochMillis) {
        return YEAR_MONTH.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
