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

    /** 关注数：关注该吧的人数 */
    private Integer followCount;

    /** 帖子数：该吧未删除的帖子数 */
    private Integer postCount;

    /** 吧主user_id；历史数据可能为空 */
    private Long ownerUserId;

    /** 吧主昵称；吧主已注销或该吧是无主数据时为 null */
    private String ownerNickName;

    /** 状态：1正常，0停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
