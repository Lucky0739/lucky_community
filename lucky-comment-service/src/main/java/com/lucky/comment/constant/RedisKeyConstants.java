package com.lucky.comment.constant;

/**
 * 评论服务的 Redis key。
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /** 帖子评论总数 Hash：comment:total:{postId} */
    public static final String NOTE_COMMENT_TOTAL_KEY = "comment:total:";

    /** 帖子一级评论列表 ZSet：comment:list:{postId}。member=评论 ID，score=发布时间(epoch 毫秒)，升序 */
    public static final String NOTE_COMMENT_LIST_KEY = "comment:list:";

    /** 单条评论详情缓存：comment:detail:{commentId}，值是评论 VO 的 JSON */
    public static final String COMMENT_DETAIL_KEY = "comment:detail:";

    /** 一级评论下二级回复列表 ZSet：comment:reply:list:{rootCommentId} */
    public static final String REPLY_COMMENT_LIST_KEY = "comment:reply:list:";

    /** 一级评论下二级回复总数 Hash：comment:reply:total:{rootCommentId} */
    public static final String REPLY_COMMENT_TOTAL_KEY = "comment:reply:total:";

    /** 评论点赞数：comment:like:count:{commentId} */
    public static final String COMMENT_LIKE_COUNT_KEY = "comment:like:count:";

    /** 某评论的点赞用户集合：comment:like:users:{commentId} */
    public static final String COMMENT_LIKE_USERS_KEY = "comment:like:users:";

    /**
     * Hash Field 键：评论总数
     */
    public static final String FIELD_COMMENT_TOTAL = "commentTotal";

    public static final String FIELD_REPLY_TOTAL = "replyTotal";

    /** 构建帖子评论总数 Hash 的 key */
    public static String buildNoteCommentTotalKey(Long postId) {
        return NOTE_COMMENT_TOTAL_KEY + postId;
    }

    /** 构建帖子一级评论列表 ZSet 的 key */
    public static String buildNoteCommentListKey(Long postId) {
        return NOTE_COMMENT_LIST_KEY + postId;
    }

    /** 构建单条评论详情缓存的 key */
    public static String buildCommentDetailKey(Long commentId) {
        return COMMENT_DETAIL_KEY + commentId;
    }

    public static String buildReplyCommentListKey(Long rootCommentId) {
        return REPLY_COMMENT_LIST_KEY + rootCommentId;
    }

    public static String buildReplyCommentTotalKey(Long rootCommentId) {
        return REPLY_COMMENT_TOTAL_KEY + rootCommentId;
    }
    public static String buildCommentLikeCountKey(Long commentId) {
        return COMMENT_LIKE_COUNT_KEY + commentId;
    }

    public static String buildCommentLikeUsersKey(Long commentId) {
        return COMMENT_LIKE_USERS_KEY + commentId;
    }
}
