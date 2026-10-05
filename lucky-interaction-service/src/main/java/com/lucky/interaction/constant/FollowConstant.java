package com.lucky.interaction.constant;

/**
 * 关注域常量：Redis key、人数上限、落库窗口、RocketMQ 目的地
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

    /** 待同步到 tb_user_info.followee 的用户 ID */
    public static final String FOLLOWEE_DIRTY_KEY = "follow:stat:dirty";

    /** 本窗口内已发生的关注次数，到 500 就刷库 */
    public static final String FOLLOWEE_EVENT_KEY = "follow:stat:events";

    /** 本窗口是否已投递 10 秒延迟刷库消息 */
    public static final String FOLLOWEE_SCHEDULED_KEY = "follow:stat:scheduled";

    public static String followUserKey(Long userId) {
        return String.format(FOLLOW_USER_ZSET, userId);
    }

    public static String followCountKey(Long userId) {
        return String.format(FOLLOW_COUNT, userId);
    }

    /** 单用户最多关注人数 */
    public static final int MAX_FOLLOWEE = 2000;

    /** 窗口内关注次数达到该值立即刷 user_info */
    public static final int FLUSH_EVENT_THRESHOLD = 500;

    public static final String MQ_TOPIC = "follow-topic";

    public static final String MQ_TAG = "FOLLOW";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_CONSUMER_GROUP = "follow-persist-consumer";

    public static final String MQ_FLUSH_TAG = "FOLLOW_COUNT_FLUSH";

    public static final String MQ_FLUSH_DESTINATION = MQ_TOPIC + ":" + MQ_FLUSH_TAG;

    public static final String MQ_FLUSH_CONSUMER_GROUP = "follow-count-flush-consumer";

    /**
     * RocketMQ 延迟级别：1s 5s 10s …，3 对应 10 秒
     */
    public static final int MQ_DELAY_LEVEL_10S = 3;

    public static final long MQ_SEND_TIMEOUT_MS = 3000L;

    /** 刷库缓冲 Lua：本窗口已有延迟消息，只需累加 */
    public static final long STAT_NONE = 0L;

    /** 刷库缓冲 Lua：本窗口第一次关注，需要投递 10 秒延迟消息 */
    public static final long STAT_SCHEDULE = 1L;

    /** 刷库缓冲 Lua：窗口内关注次数达到阈值，立即刷库 */
    public static final long STAT_FLUSH = 2L;
}
