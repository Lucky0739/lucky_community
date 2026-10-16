package com.lucky.interaction.constant;

/**
 * 关注域常量：Redis key、人数上限、RocketMQ 目的地
 */
public final class FollowConstant {

    private FollowConstant() {
    }
    /**
     * 关注的贴吧 ZSet。member 是贴吧 ID，score 是关注时间毫秒。
     */
    private static final String FOLLOW_BAR_ZSET = "follow:bar:%s";

    /** 贴吧最近帖子发件箱。member 是 barId:postId，score 是发帖时间毫秒。大吧靠用户来拉。 */
    private static final String BAR_OUTBOX = "bar:outbox:%s";

    /** 用户关注流。推送和用户主动拉取都写到这里。 */
    private static final String FOLLOW_FEED = "feed:follow:%s";

    /** 该用户当前关注数，和 ZSet 一起由 Lua 维护 */
    private static final String FOLLOW_COUNT = "follow:count:%s";

    public static String followUserKey(Long userId) {
        return String.format(FOLLOW_BAR_ZSET, userId);
    }

    public static String barOutboxKey(Long barId) {
        return String.format(BAR_OUTBOX, barId);
    }

    public static String followFeedKey(Long userId) {
        return String.format(FOLLOW_FEED, userId);
    }

    public static String followCountKey(Long userId) {
        return String.format(FOLLOW_COUNT, userId);
    }

    /** 单用户最多关注贴吧数 */
    public static final int MAX_FOLLOWEE = 2000;

    /**
     * 粉丝数达到这个值的贴吧走拉模式，由用户把发件箱拉进自己的关注流。
     * 低于这个值的贴吧走推模式，发帖时写进每个关注者的关注流。
     */
    public static final int PULL_FANS_THRESHOLD = 1000;

    /** 每个贴吧发件箱保留的最新帖子数 */
    public static final int BAR_OUTBOX_SIZE = 200;

    /** 用户关注流最多保留多少条 */
    public static final int FOLLOW_FEED_SIZE = 500;

    public static final String MQ_TOPIC = "follow-topic";

    public static final String MQ_TAG = "FOLLOW";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "follow-persist-consumer";

    public static final String MQ_UNFOLLOW_TAG = "UNFOLLOW";

    public static final String MQ_UNFOLLOW_DESTINATION = MQ_TOPIC + ":" + MQ_UNFOLLOW_TAG;
}
