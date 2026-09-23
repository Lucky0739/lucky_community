package com.lucky.core.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 双 Token 返回体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenDTO {

    /** 短 token(访问令牌)：返回给前端，放内存 */
    private String accessToken;

    /** 长 token(刷新令牌)：写入 HttpOnly Cookie，用于续期 */
    private String refreshToken;
}
