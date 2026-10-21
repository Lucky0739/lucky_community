package com.lucky.community.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 贴吧信息
 */
@Data
public class BarVO {

    /** 贴吧ID */
    private Long barId;

    /** 吧名 */
    private String name;

    /** 吧简介 */
    private String description;

    /** 吧头像URL */
    private String avatar;

    /** 粉丝数：关注该吧的人数，来自 tb_bar_count.fan_count */
    private Integer followCount;

    /** 帖子数：该吧未删除的帖子数 */
    private Integer postCount;

    /** 当前用户是否已关注该吧；未登录或未带 token 时为 false */
    private Boolean followed;

    /** 吧主user_id；历史数据可能为空 */
    private Long ownerUserId;

    /** 吧主昵称；吧主已注销或该吧是无主数据时为 null */
    private String ownerNickName;

    /** 状态：1正常，0停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 吧外预览：每个吧只带最新一条可见帖。
     * 进吧后的完整列表走帖子流，并带上 barId。
     */
    private PostSummaryVO latestPost;
}
