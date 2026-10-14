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
 *
 * <p>注意：KV 的这两个端点返回的是 {@link Result}（success / errorMsg / data），
 * <b>不是</b> {@link com.lucky.common.Response}（code / msg / data）。成功判定只能看
 * {@code success} 字段 —— 别把评论服务那套 {@code getCode() != Response.CODE_SUCCESS}
 * 抄过来，评论的批量写正文才返回 {@code Response}。
 *
 * <p>另外 KV 侧把业务异常交给全局异常处理器转成了「HTTP 200 + Result.fail」，
 * 所以失败不会以 5xx 形式出现，同样要靠 {@code success} 判断。
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
     *
     * <p><b>软失败</b>：读不到就返回 null，由调用方回退到 {@code content_preview}。
     * 正文读取不该让详情页 500 —— 摘要总还是有的。
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
     * 尽力而为：失败只记日志，不能影响已经提交的删帖。
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
