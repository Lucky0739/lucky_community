package com.lucky.core.bar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 贴吧表
 */
@Data
@TableName("tb_bar")
public class Bar {

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** 主键(雪花ID) */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

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

    /** 吧主user_id */
    private Long ownerUserId;

    /** 状态：1正常，0停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
