package com.lucky.search.service;

import com.lucky.common.mq.PostIndexMessage;
import com.lucky.search.domain.PostDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

/**
 * 把帖子索引事件落到 ES。
 *
 * <p>{@link PostDocument} 的 {@code @Id} 就是 postId，所以写入天然幂等 ——
 * 重复投递、兜底补发、全量重建都安全，同一条帖只会有一份文档。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostIndexService {

    private final ElasticsearchOperations elasticsearchOperations;

    public void apply(PostIndexMessage message) {
        Long postId = message.getPostId();
        if (postId == null) {
            log.warn("索引事件缺少 postId，丢弃：{}", message);
            return;
        }
        if (!Boolean.TRUE.equals(message.getVisible())) {
            // 不可见的事件只带 postId，按 id 删即可
            elasticsearchOperations.delete(String.valueOf(postId), PostDocument.class);
            log.info("帖子已撤出索引，postId={}", postId);
            return;
        }
        elasticsearchOperations.save(toDocument(message, postId));
        log.info("帖子已进索引，postId={}", postId);
    }

    private PostDocument toDocument(PostIndexMessage message, Long postId) {
        PostDocument document = new PostDocument();
        document.setPostId(postId);
        document.setBarId(message.getBarId());
        document.setUserId(message.getUserId());
        document.setTitle(message.getTitle());
        document.setContent(message.getContent());
        document.setCoverImage(message.getCoverImage());
        document.setVisibilityScope(message.getVisibilityScope());
        document.setDeleted(message.getDeleted());
        document.setLikeCount(message.getLikeCount());
        document.setCreateTime(message.getCreateTime());
        return document;
    }
}
