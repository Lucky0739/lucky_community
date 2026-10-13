package com.lucky.interaction.constant;

/**
 * 关注域常量：Redis key、人数上限、RocketMQ 目的地
 */
public final class FollowConstant {

    private FollowConstant() {
    }
    /**
     * 关注列表 ZSet。member 是被关注用户 ID，score 是关注时间毫秒。
     */
    private static final String FOLLOW_USER_ZSET = "follow:user:%s";

    /** 该用户当前关注数，和 ZSet 一起由 Lua 维护 */
    private static final String FOLLOW_COUNT = "follow:count:%s";

    public static String followUserKey(Long userId) {
        return String.format(FOLLOW_USER_ZSET, userId);
    }

    public static String followCountKey(Long userId) {
        return String.format(FOLLOW_COUNT, userId);
    }

    /** 单用户最多关注人数 */
    public static final int MAX_FOLLOWEE = 2000;

    public static final String MQ_TOPIC = "follow-topic";

    public static final String MQ_TAG = "FOLLOW";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "follow-persist-consumer";

    public static final String MQ_UNFOLLOW_TAG = "UNFOLLOW";

    public static final String MQ_UNFOLLOW_DESTINATION = MQ_TOPIC + ":" + MQ_UNFOLLOW_TAG;
}
