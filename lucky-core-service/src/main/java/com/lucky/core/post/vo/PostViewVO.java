package com.lucky.core.post.vo;

import com.lucky.core.comment.vo.CommentVO;
import lombok.Data;

import java.util.List;

/**
 * 帖子详情首屏：帖子 + 一级评论首页
 * <p>评论不进 {@code post:detail} 缓存。展开某楼回复走 {@code GET /comment/replies}，
 * 继续翻楼层走 {@code GET /comment/page}。
 */
@Data
public class PostViewVO {

    private PostDetailVO post;

    /** 一级评论首页，不含回复正文；楼下条数看 {@link CommentVO#getReplyCount()} */
    private List<CommentVO> comments;

    /** 一级评论总数，给前端接着翻页用；帖子上的 {@code commentCount} 仍是含回复的全量 */
    private Long rootCommentTotal;
}
