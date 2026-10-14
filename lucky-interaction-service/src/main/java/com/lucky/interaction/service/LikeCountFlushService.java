package com.lucky.interaction.service;

public interface LikeCountFlushService {

    /** 热路径：点赞/取消点赞改动了 Redis 计数后调用 */
    void onLikeCountChanged(Long postId);

    /** 消费端：把脏帖子的 Redis 绝对值写入 tb_post_count */
    void flushToDatabase();
}
