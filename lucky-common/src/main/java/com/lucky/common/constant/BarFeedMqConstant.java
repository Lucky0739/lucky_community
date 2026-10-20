package com.lucky.common.constant;

/**
 * 新帖进入关注流。生产者是 post-service，消费者是 interaction-service。
 */
public final class BarFeedMqConstant {

    private BarFeedMqConstant() {
    }

    public static final String MQ_TOPIC = "bar-feed-topic";

    public static final String MQ_TAG = "PUBLISH";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "bar-feed-consumer";
}
