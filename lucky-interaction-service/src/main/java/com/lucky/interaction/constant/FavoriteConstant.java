package com.lucky.interaction.constant;

/**
 * 收藏域：布隆容量、key 过期时间、落库消息目的地
 */
public final class FavoriteConstant {

    private FavoriteConstant() {
    }

    public static final String BLOOM_ERROR_RATE = "0.01";
    public static final long BLOOM_CAPACITY = 10_000L;
    public static final long BLOOM_TTL_SECONDS = 7L * 24 * 3600;
    public static final long DATA_TTL_SECONDS = BLOOM_TTL_SECONDS;
    public static final long COUNT_TTL_SECONDS = 30L * 60;

    public static final String MQ_TOPIC = "favorite-topic";
    public static final String MQ_TAG = "FAVORITE";
    public static final String MQ_DESTINATION = MQ_TOPIC + ":" + MQ_TAG;
    public static final String MQ_UNFAVORITE_TAG = "UNFAVORITE";
    public static final String MQ_UNFAVORITE_DESTINATION = MQ_TOPIC + ":" + MQ_UNFAVORITE_TAG;
    public static final String MQ_CONSUMER_GROUP = "favorite-persist-consumer";

    public static final int CONSUME_BATCH_MAX = 30;
    public static final int PERSIST_BATCH_SIZE = 500;
    public static final int PERSIST_LINGER_SECONDS = 5;
    public static final int PERSIST_BUFFER_SIZE = 10_000;

    public static final int FLUSH_EVENT_THRESHOLD = 500;
    public static final int MQ_DELAY_LEVEL_10S = 3;
    public static final String MQ_FLUSH_TAG = "FAVORITE_COUNT_FLUSH";
    public static final String MQ_FLUSH_DESTINATION = MQ_TOPIC + ":" + MQ_FLUSH_TAG;
    public static final String MQ_FLUSH_CONSUMER_GROUP = "favorite-count-flush-consumer";
}
