package com.lucky.comment.rpc;

import com.lucky.comment.mq.PublishCommentMessage;
import com.lucky.common.Response;
import com.lucky.common.exception.BusinessException;
import com.lucky.kv.api.api.KeyValueFeignApi;
import com.lucky.kv.api.dto.BatchAddCommentContentDTO;
import com.lucky.kv.api.dto.CommentContentDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
