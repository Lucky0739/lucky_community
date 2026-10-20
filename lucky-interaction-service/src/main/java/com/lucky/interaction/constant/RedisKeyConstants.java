package com.lucky.interaction.constant;

/**
 * 点赞布隆过滤器的 Redis key。每个用户一把，只服务点赞，不和贴吧布隆、帖子布隆混用。
 */
public class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /**
     * 用户点赞布隆：bloom:post:likes:{userId}
     */
    public static final String BLOOM_USER_POST_LIKE_LIST_KEY = "bloom:post:likes:";

    /** 点赞已写入 Redis、数据库消息尚未确认：thumb:unsynced:{userId}:{postId} -> 雪花 ID */
    public static final String THUMB_UNSYNCED_KEY = "thumb:unsynced:";

    /**
     * 取消点赞集合：thumb:cancel:{userId}。布隆位不能删除，成员是已取消的帖子 ID。
     */
    public static final String THUMB_CANCEL_KEY = "thumb:cancel:";

    /**
     * 构建用户点赞布隆的完整 key
     */
    public static String buildBloomUserPostLikeListKey(Long userId) {
        return BLOOM_USER_POST_LIKE_LIST_KEY + userId;
    }

    public static String buildThumbUnsyncedKey(Long userId, Long postId) {
        return THUMB_UNSYNCED_KEY + userId + ":" + postId;
    }

    public static String buildThumbCancelKey(Long userId) {
        return THUMB_CANCEL_KEY + userId;
    }

    /** 待刷入 tb_post_count 的 postId 集合 */
    public static final String THUMB_LIKE_COUNT_DIRTY_SET = "thumb:like:count:dirty";

    /** 点赞计数变更事件累计，达到阈值触发刷库 */
    public static final String THUMB_LIKE_COUNT_EVENT_COUNTER = "thumb:like:count:events";

    /** 10 秒延迟刷库定时器占位，避免重复发延迟消息 */
    public static final String THUMB_LIKE_COUNT_FLUSH_SCHEDULED = "thumb:like:count:flush:scheduled";

    /** 点赞数刷库锁：同一帖子的「读 Redis → 写 DB」串行化，防止旧值覆盖新值 */
    public static final String THUMB_LIKE_COUNT_FLUSH_LOCK_KEY = "thumb:like:count:flush:lock:";

    public static String buildThumbLikeCountFlushLockKey(Long postId) {
        return THUMB_LIKE_COUNT_FLUSH_LOCK_KEY + postId;
    }

    /** 用户收藏布隆：bloom:post:favorites:{userId} */
    public static final String BLOOM_USER_POST_FAVORITE_LIST_KEY = "bloom:post:favorites:";

    /** 收藏已写入 Redis、数据库消息尚未确认：favorite:unsynced:{userId}:{postId} */
    public static final String FAVORITE_UNSYNCED_KEY = "favorite:unsynced:";

    /** 取消收藏集合：favorite:cancel:{userId} */
    public static final String FAVORITE_CANCEL_KEY = "favorite:cancel:";

    /** 帖子收藏计数：favorite:count:{postId} */
    public static final String FAVORITE_COUNT_KEY = "favorite:count:";

    /** 用户收藏 Hash：favorite:user:{userId} */
    public static final String FAVORITE_USER_KEY = "favorite:user:";

    public static String buildBloomUserPostFavoriteListKey(Long userId) {
        return BLOOM_USER_POST_FAVORITE_LIST_KEY + userId;
    }

    public static String buildFavoriteUnsyncedKey(Long userId, Long postId) {
        return FAVORITE_UNSYNCED_KEY + userId + ":" + postId;
    }

    public static String buildFavoriteCancelKey(Long userId) {
        return FAVORITE_CANCEL_KEY + userId;
    }

    public static String buildFavoriteCountKey(Long postId) {
        return FAVORITE_COUNT_KEY + postId;
    }

    public static String buildFavoriteUserKey(Long userId) {
        return FAVORITE_USER_KEY + userId;
    }

    public static final String FAVORITE_COUNT_DIRTY_SET = "favorite:count:dirty";
    public static final String FAVORITE_COUNT_EVENT_COUNTER = "favorite:count:events";
    public static final String FAVORITE_COUNT_FLUSH_SCHEDULED = "favorite:count:flush:scheduled";

    /** 收藏数刷库锁：同一帖子的「读 Redis → 写 DB」串行化，防止旧值覆盖新值 */
    public static final String FAVORITE_COUNT_FLUSH_LOCK_KEY = "favorite:count:flush:lock:";

    public static String buildFavoriteCountFlushLockKey(Long postId) {
        return FAVORITE_COUNT_FLUSH_LOCK_KEY + postId;
    }
}
