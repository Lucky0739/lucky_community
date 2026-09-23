package com.lucky.core.user.dto;

import com.lucky.common.dto.OffsetPageQueryDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员查询用户请求类（走查询参数，非请求体）
 * <p>分页字段（pageNum / pageSize）继承自 {@link OffsetPageQueryDTO}
 * <p>字段为 null/空表示不参与筛选
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryDTO extends OffsetPageQueryDTO {

    /**
     * 手机号前缀（前缀匹配，能走 uniqe_key_phone 索引）
     * <p>刻意不加手机号格式校验：这是前缀搜索，"139" 是合法入参
     */
    @Size(max = 11, message = "手机号过长")
    private String phone;

    /** 昵称关键字（模糊匹配） */
    @Size(max = 32, message = "昵称过长")
    private String nickName;
}
