package com.lucky.community.vo;

import com.lucky.community.ai.vo.CommentVO;
import lombok.Data;

import java.util.List;

/**
 * 帖子详情首屏：帖子 + 一级评论首页
 */
@Data
public class PostViewVO {

    private PostDetailVO postDetailVO;

    /** 一级评论首页，不含回复正文；楼下条数看 {@link CommentVO#getReplyCount()} */
    private List<CommentVO> comments;

    /** 一级评论总数，给前端接着翻页用；帖子上的 {@code commentCount} 仍是含回复的全量 */
    private Long rootCommentTotal;
}
