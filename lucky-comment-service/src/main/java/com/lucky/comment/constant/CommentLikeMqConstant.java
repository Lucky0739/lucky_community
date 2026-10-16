package com.lucky.comment.constant;

public final class CommentLikeMqConstant {

    private CommentLikeMqConstant() {
    }

    public static final String MQ_TOPIC = "comment-like-topic";

    public static final String MQ_TAG = "LIKE";

    public static final String MQ_UNLIKE_TAG = "UNLIKE";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_UNLIKE_DESTINATION = MQ_TOPIC + ":" + MQ_UNLIKE_TAG;

    public static final String MQ_CONSUMER_GROUP = "comment-like-persist-consumer";

    public static final int CONSUME_BATCH_MAX = 50;
}
