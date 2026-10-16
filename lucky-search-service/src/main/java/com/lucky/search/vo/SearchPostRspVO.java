package com.lucky.search.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索帖子的返回结果。字段来自 post 索引。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchPostRspVO {

    /** tb_post.id */
    private Long postId;

    /** tb_post.bar_id */
    private Long barId;

    /** tb_post.user_id */
    private Long userId;

    /** tb_post.cover_image */
    private String coverImage;

    /** tb_post.title */
    private String title;

    /** 标题高亮，命中词用 strong 包住 */
    private String highlightTitle;

    /** tb_post_count.like_count */
    private Long likeCount;

    /** tb_post.create_time */
    private LocalDateTime createTime;
}
