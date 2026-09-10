package com.lucky.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户扩展信息表
 */
@Data
@TableName("tb_user_info")
public class UserInfo {

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户id */
    private Long userId;

    /** 城市名称 */
    private String city;

    /** 个人介绍，不要超过128个字符 */
    private String introduce;

    /** 粉丝数量 */
    private Integer fans;

    /** 关注的人的数量 */
    private Integer followee;

    /** 性别，0：男，1：女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
