package com.lucky.interaction.vo;

import lombok.Data;

/**
 * 首页等场景按 barId 批量查贴吧名、粉丝数、帖子数及当前用户是否已关注。
 */
@Data
public class FollowBarSnapshotVO {

    private Long barId;
    private String name;
    private String avatar;
    /** 粉丝数，来自 tb_bar_count.fan_count */
    private Integer followCount;
    private Integer postCount;
    private Boolean followed;
}
