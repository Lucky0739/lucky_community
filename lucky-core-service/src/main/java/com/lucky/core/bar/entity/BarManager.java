package com.lucky.core.bar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 贴吧吧务任命表
 */
@Data
@TableName("tb_bar_manager")
public class BarManager {

    /** 任命状态：生效 */
    public static final int STATUS_ACTIVE = 1;
    /** 任命状态：撤销 */
    public static final int STATUS_REVOKED = 0;

    /** 主键ID(自增) */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 贴吧ID */
    private Long barId;

    /** 被任命用户ID(吧务) */
    private Long userId;

    /** 任命人用户ID(平台管理员) */
    private Long grantUserId;

    /** 任命时间 */
    private LocalDateTime grantTime;

    /** 状态：1生效，0撤销 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
