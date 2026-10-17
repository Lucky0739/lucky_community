package com.lucky.comment.rpc;

import com.lucky.comment.mq.message.PublishCommentMessage;
import com.lucky.common.Response;
import com.lucky.common.exception.BusinessException;
import com.lucky.kv.api.api.KeyValueFeignApi;
import com.lucky.common.Result;
import com.lucky.kv.api.dto.BatchAddCommentContentDTO;
import com.lucky.kv.api.dto.CommentContentDTO;
import com.lucky.kv.api.dto.FindCommentContentDTO;
import com.lucky.kv.api.dto.DeleteCommentContentDTO;
import com.lucky.kv.api.dto.FindCommentContentsDTO;
import com.lucky.kv.api.vo.CommentContentItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeyValueRpcService {

    private final KeyValueFeignApi keyValueFeignApi;

    /**
     * 批量存储评论正文。KV 返回失败时抛异常，让调用方回滚事务。
     */
    public boolean batchSaveCommentContent(List<PublishCommentMessage> messages) {
        List<CommentContentDTO> comments = messages.stream()
                .map(message -> CommentContentDTO.builder()
                        .postId(message.getPostId())
                        .content(message.getContent())
                        .contentId(message.getContentUuid())
                        .yearMonth(message.getYearMonth())
                        .build())
                .toList();
        BatchAddCommentContentDTO request = BatchAddCommentContentDTO.builder()
                .comments(comments)
                .build();
        Response<?> response = keyValueFeignApi.batchAddCommentContent(request);
        if (response == null || response.getCode() == null || response.getCode() != Response.CODE_SUCCESS) {
            throw new BusinessException("评论正文写入失败");
        }
        return true;
    }

    /**
     * 批量读取评论正文。
     */
    public List<CommentContentItemVO> batchFindCommentContent(Long postId, List<FindCommentContentDTO> keys) {
        if (postId == null || CollectionUtils.isEmpty(keys)) {
            return List.of();
        }
        List<FindCommentContentDTO> comments = keys.stream()
                .map(key -> FindCommentContentDTO.builder()
                        .postId(postId)
                        .yearMonth(key.getYearMonth())
                        .contentId(key.getContentId())
                        .build())
                .toList();
        Result<List<CommentContentItemVO>> result = keyValueFeignApi.findCommentContents(
                FindCommentContentsDTO.builder().comments(comments).build());
        if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || result.getData() == null) {
            return List.of();
        }
        return result.getData();
    }

    public void deleteCommentContent(Long postId, String yearMonth, String contentId) {
        try {
            Response<Void> response = keyValueFeignApi.deleteCommentContent(DeleteCommentContentDTO.builder()
                    .postId(postId)
                    .yearMonth(yearMonth)
                    .contentId(contentId)
                    .build());
            if (response == null || response.getCode() == null || response.getCode() != Response.CODE_SUCCESS) {
                log.warn("删除评论正文失败，postId={}，contentId={}", postId, contentId);
            }
        } catch (RuntimeException e) {
            log.warn("删除评论正文异常，postId={}，contentId={}", postId, contentId, e);
        }
    }
}
