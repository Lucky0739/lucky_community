package com.lucky.community.rpc;

import com.lucky.common.Result;
import com.lucky.common.exception.BusinessException;
import com.lucky.kv.api.api.KeyValueFeignApi;
import com.lucky.kv.api.dto.CreatePostContentRequest;
import com.lucky.kv.api.dto.DeletePostContentDTO;
import com.lucky.kv.api.dto.FindPostContentDTO;
import com.lucky.kv.api.vo.FindPostContentVO;
import com.lucky.kv.api.vo.PostContentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 帖子正文（Cassandra，落在 KV 服务）的读写封装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyValueRpcService {

    private final KeyValueFeignApi keyValueFeignApi;

    /**
     * 写入正文，返回 Cassandra 主键（{@code new UUID(0, postId)}）。
     * 失败抛异常，让调用方的事务回滚 —— 正文是帖子的本体，不能静默丢。
     */
    public String create(Long postId, String content) {
        CreatePostContentRequest request = new CreatePostContentRequest();
        request.setPostId(postId);
        request.setContent(content);

        Result<PostContentVO> result;
        try {
            result = keyValueFeignApi.create(request);
        } catch (RuntimeException e) {
            log.error("调用 KV 写入帖子正文失败，postId={}", postId, e);
            throw new BusinessException("帖子正文写入失败");
        }
        if (result == null
                || !Boolean.TRUE.equals(result.getSuccess())
                || result.getData() == null
                || result.getData().getContentUuid() == null) {
            log.error("KV 返回写入正文失败，postId={}，errorMsg={}",
                    postId, result == null ? "null" : result.getErrorMsg());
            throw new BusinessException("帖子正文写入失败");
        }
        return result.getData().getContentUuid().toString();
    }

    /**
     * 按 Cassandra 主键读正文。
     */
    public String find(String contentUuid) {
        if (!StringUtils.hasText(contentUuid)) {
            return null;
        }
        FindPostContentDTO request = new FindPostContentDTO();
        request.setPostId(contentUuid);

        try {
            Result<FindPostContentVO> result = keyValueFeignApi.find(request);
            if (result == null
                    || !Boolean.TRUE.equals(result.getSuccess())
                    || result.getData() == null) {
                return null;
            }
            return result.getData().getContent();
        } catch (RuntimeException e) {
            log.warn("读取帖子正文失败，contentUuid={}，将回退到摘要", contentUuid, e);
            return null;
        }
    }
    /**
     * 删帖时清掉正文。Cassandra 那张表没有 TTL，留着会永久堆积。
     */
    public void delete(String contentUuid) {
        if (!StringUtils.hasText(contentUuid)) {
            return;
        }
        try {
            keyValueFeignApi.deleteNoteContent(
                    DeletePostContentDTO.builder().uuid(contentUuid).build());
        } catch (RuntimeException e) {
            log.warn("删除帖子正文失败，contentUuid={}", contentUuid, e);
        }
    }
}
