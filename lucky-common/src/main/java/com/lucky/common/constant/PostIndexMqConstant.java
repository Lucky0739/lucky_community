package com.lucky.common.constant;

/**
 * 帖子索引变更事件的 MQ 契约。
 *
 * <p>生产者是 post-service，消费者是 search-service —— 两边必须用同一份定义，
 * 所以放在 common 而不是任一侧（同 {@link CommentConstant} 的处境）。
 */
public final class PostIndexMqConstant {

    private PostIndexMqConstant() {
    }

    public static final String MQ_TOPIC = "post-index-topic";

    public static final String MQ_TAG = "INDEX";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "post-index-consumer";

    /** 一批最多消费这么多条 */
    public static final int CONSUME_BATCH_MAX = 32;

    /** 补发任务每轮最多处理多少条兜底消息 */
    public static final int OUTBOX_BATCH = 20;
}
