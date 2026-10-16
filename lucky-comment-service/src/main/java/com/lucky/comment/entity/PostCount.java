package com.lucky.comment.entity;

import lombok.Data;

/**
 * 帖子点赞 / 收藏 / 评论计数（对应 tb_post_count）。
 * 评论服务只写 comment_count，其余列由别的服务维护。
 */
@Data
public class PostCount {

    /** 帖子ID，主键，指向 tb_post.id */
    private Long postId;

    /** 点赞数，与 tb_thumb 对账 */
    private Long likeCount;

    /** 收藏数，与 tb_favorite 对账 */
    private Long favoriteCount;

    /** 评论数，与 tb_comment 对账 */
    private Long commentCount;
}
