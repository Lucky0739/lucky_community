package com.lucky.user.auth.dto;

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

    private String accessToken;
    private String refreshToken;
}
