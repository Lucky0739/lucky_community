package com.lucky.comment.constant;

public final class CommentMqConstant {

    private CommentMqConstant() {
    }

    public static final String MQ_TOPIC = "comment-topic";

    public static final String MQ_TAG = "PUBLISH";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "comment-persist-consumer";

    /** 删除评论：和 PUBLISH 共用 topic，用 tag 区分 */
    public static final String MQ_DELETE_TAG = "DELETE";

    public static final String MQ_DELETE_DESTINATION = MQ_TOPIC + ":" + MQ_DELETE_TAG;

    /** 删除必须用独立消费组：否则和 PUBLISH 组之间会互相瓜分消息 */
    public static final String MQ_DELETE_CONSUMER_GROUP = "comment-delete-consumer";

    /** 一批最多消费这么多条，然后一起写入数据库 */
    public static final int CONSUME_BATCH_MAX = 30;

    /** 热度榜 key，和帖子服务的 bar:hot 是同一个 */
    public static final String BAR_HOT_KEY = "bar:hot";
}
