package com.lucky.community.mq;

import com.lucky.community.entity.Post;
import com.lucky.community.mapper.PostAnalysisMapper;
import com.lucky.community.mapper.PostMapper;
import com.lucky.common.mq.PostIndexMessage;
import com.lucky.community.rpc.KeyValueRpcService;
import com.lucky.community.support.PostEngagementSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 帖子索引变更事件的唯一出口。
 *
 * <p>所有「可能改变帖子可见性」的地方都调这里的 {@link #emit}，而不是各自判断要不要索引 ——
 * 可见性规则只在这里现算一次（复用 {@code PostAnalysisMapper#countPubliclyVisible}），
 * 免得散落各处、改一处漏一处。
 *
 * <p><b>调用方要自己挂到事务提交之后</b>（{@code PostServiceImpl#registerAfterCommit}）。
 * 本方法内部吞掉所有异常：索引是派生数据，任何失败都不能影响发帖/改帖/删帖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostIndexEmitter {

    private final PostMapper postMapper;
    private final PostAnalysisMapper postAnalysisMapper;
    private final PostEngagementSupport postEngagementSupport;
    private final KeyValueRpcService keyValueRpcService;
    private final PostIndexMqSender postIndexMqSender;

    /**
     * 发一条索引变更事件。
     *
     * @param contentOrNull 调用方手上已有的正文；传 null 就从 Cassandra 读（多一次 RPC）
     */
    public void emit(Long postId, String contentOrNull) {
        try {
            PostIndexMessage message = build(postId, contentOrNull);
            postIndexMqSender.send(message);
        } catch (Exception e) {
            // 发送失败时 PostIndexMqSender 的 @Recover 已落兜底表；这里只兜住更外层的意外
            log.error("投递帖子索引事件失败，postId={}", postId, e);
        }
    }

    private PostIndexMessage build(Long postId, String contentOrNull) {
        PostIndexMessage message = new PostIndexMessage();
        message.setPostId(postId);

        // selectById 带 deleted = 0，逻辑删帖会返回 null。这种情况只需要让索引侧删文档，
        // 其余字段没必要填 —— 但正是「删帖」这个场景最需要发出这条消息。
        Post post = postMapper.selectById(postId);
        if (post == null) {
            message.setVisible(false);
            return message;
        }

        boolean visible = postAnalysisMapper.countPubliclyVisible(postId) > 0;
        message.setVisible(visible);
        if (!visible) {
            // 不可见就只发 postId，消费端按 id 删文档
            return message;
        }

        message.setBarId(post.getBarId());
        message.setUserId(post.getUserId());
        message.setTitle(post.getTitle());
        message.setContent(contentOrNull != null
                ? contentOrNull
                : keyValueRpcService.findPostBody(post.getId(), post.getContentUuid()));
        message.setCoverImage(post.getCoverImage());
        message.setVisibilityScope(post.getVisibilityScope() == null ? 0 : post.getVisibilityScope());
        message.setDeleted(post.getDeleted());
        message.setCreateTime(post.getCreateTime());
        // 点赞数取自 tb_thumb 的聚合，只有这一处用它排序，单帖查询成本可接受
        message.setLikeCount(postEngagementSupport.getLikeCounts(List.of(postId))
                .getOrDefault(postId, 0L));
        return message;
    }
}
