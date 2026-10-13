package com.lucky.comment.constant;

public final class CommentMqConstant {

    private CommentMqConstant() {
    }

    public static final String MQ_TOPIC = "comment-topic";

    public static final String MQ_TAG = "PUBLISH";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "comment-persist-consumer";

    /** 一批最多消费这么多条，然后一起写入数据库 */
    public static final int CONSUME_BATCH_MAX = 30;

    /** 热度榜 key，和帖子服务的 bar:hot 是同一个 */
    public static final String BAR_HOT_KEY = "bar:hot";
}
