package com.lucky.user.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员视角的用户详情
 * <p>列表字段 + tb_user_info 的扩展资料；同样不含 password
 */
@Data
public class AdminUserDetailVO {

    /** 用户ID */
    private Long userId;

    /** 手机号码（已注销的用户为 null） */
    private String phone;

    /** 昵称 */
    private String nickName;

    /** 人物头像 */
    private String icon;

    /** 角色列表 */
    private List<RoleVO> roles;

    /** 城市名称 */
    private String city;

    /** 个人介绍 */
    private String introduce;

    /** 粉丝数量 */
    private Integer fans;

    /** 关注的人的数量 */
    private Integer followee;

    /** 获赞数 */
    private Integer likeCount;

    /** 性别：0男，1女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
