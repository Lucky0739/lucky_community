package com.lucky.interaction.constant;

/**
 * 点赞域：布隆容量、key 过期时间、落库消息目的地
 */
public final class ThumbConstant {

    private ThumbConstant() {
    }

    /** RedisBloom 误判率。BF.RESERVE 之后不能改，要改就删掉该用户的布隆 key */
    public static final String BLOOM_ERROR_RATE = "0.01";

    /** 每个用户的过滤器初始容量。超过后模块按默认扩张因子加子过滤器 */
    public static final long BLOOM_CAPACITY = 10_000L;

    /** 布隆和用户点赞 Hash 的过期时间：7 天。过期后从 tb_thumb 重建，不是没赞过 */
    public static final long BLOOM_TTL_SECONDS = 7L * 24 * 3600;

    /** 用户点赞 Hash 的过期时间，和布隆对齐。过期后从 tb_thumb 重建 */
    public static final long DATA_TTL_SECONDS = BLOOM_TTL_SECONDS;

    /** 帖子点赞计数过期时间：30 分钟。过期后用 tb_thumb 的行数灌回再加一 */
    public static final long COUNT_TTL_SECONDS = 30L * 60;

    public static final String MQ_TOPIC = "thumb-topic";

    public static final String MQ_TAG = "LIKE";

    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;

    public static final String MQ_UNLIKE_TAG = "UNLIKE";

    public static final String MQ_UNLIKE_DESTINATION = MQ_TOPIC + ":" + MQ_UNLIKE_TAG;

    public static final String MQ_CONSUMER_GROUP = "thumb-persist-consumer";
}
