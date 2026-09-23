package com.lucky.core.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@TableName("tb_user")
public class User {

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 手机号码
     * <p>逻辑删除时会置为 NULL 以释放唯一索引 uniqe_key_phone（PostgreSQL 唯一索引允许多个 NULL），
     * 因此本字段可空；号码的审计副本保留在 tb_user_phone
     */
    private String phone;

    /** 密码，加密存储 */
    private String password;

    /** 昵称，默认是用户id */
    private String nickName;

    /** 人物头像 */
    private String icon;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0未删除，1已删除
     * <p>application.yaml 的 logic-delete-field 已按字段名匹配生效，本注解可省；
     * 加在这里是为了自文档化，并防止有人删掉 yaml 配置后静默退化成物理删除。
     * <p>生效后所有针对 User 的 wrapper 查询/更新都会自动追加 AND deleted = 0，
     * 因此 login、register 查重、getById 都会自动过滤已删用户。
     * <p><b>仅 User 有此字段</b>：全局配置按字段名匹配，给没有该列的表(如 tb_user_info)
     * 加同名字段会让其查询带上 AND deleted=0 并报 Unknown column
     */
    @TableLogic
    private Integer deleted;
}
