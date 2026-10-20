package com.lucky.interaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注流里的一条帖子。来自 Redis：小吧是发帖时推入的，大吧是用户自己拉取的。
 */
@Data
public class FollowFeedItemVO {

    private Long postId;
    private Long barId;
    private LocalDateTime createTime;
}
