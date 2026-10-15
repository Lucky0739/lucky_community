package com.lucky.common.mq;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子索引变更事件。
 *
 * <p>字段与 search-service 的 {@code PostDocument} 一一对应 —— 正文随消息带过去，
 * 消费端拿到就能直接落 ES，不用回读 PostgreSQL 或 Cassandra。
 *
 * <p>生产者 post-service、消费者 search-service，两边共用这一份定义，
 * 放 common 是为了杜绝字段名漂移（序列化契约一改就是一端收不到）。
 */
@Data
public class PostIndexMessage {

    private Long postId;

    /**
     * 该帖当前是否「公开可见」。
     * true = 写入/覆盖索引文档，false = 从索引里删掉。
     *
     * <p>只索引可见帖意味着 ES 里不会留未过审内容，查询也就不必再复刻一遍可见性规则。
     */
    private Boolean visible;

    private Long barId;

    private Long userId;

    private String title;

    /** 完整正文，来自 Cassandra */
    private String content;

    private String coverImage;

    private Integer visibilityScope;

    private Integer deleted;

    private Long likeCount;

    private LocalDateTime createTime;
}
