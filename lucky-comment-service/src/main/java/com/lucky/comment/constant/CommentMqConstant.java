package com.lucky.comment.constant;

public final class CommentMqConstant {

    private CommentMqConstant() {
    }

    public static final String MQ_TOPIC = "comment-topic";

    public static final String MQ_TAG = "PUBLISH";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "comment-persist-consumer";

    /** 计数主题：评论落库成功后异步刷新帖子评论数 */
    public static final String COUNT_TOPIC = "comment-count-topic";

    public static final String COUNT_TAG = "COUNT";

    public static final String COUNT_DESTINATION = COUNT_TOPIC + ":" + COUNT_TAG;

    public static final String COUNT_CONSUMER_GROUP = "comment-count-consumer";

    /** 二级评论数用独立消费组：与上面的组不能相同，否则同主题消息会被组内在实例间瓜分 */
    public static final String COUNT_CHILD_CONSUMER_GROUP = "comment-count-child-consumer";

    /** 一批最多消费这么多条，然后一起写入数据库 */
    public static final int CONSUME_BATCH_MAX = 30;

    /** 热度榜 key，和帖子服务的 bar:hot 是同一个 */
    public static final String BAR_HOT_KEY = "bar:hot";
}
