package com.lucky.common.auth;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 基于 Hutool BCrypt 的密码编码器
 */
public class HutoolPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        return BCrypt.hashpw(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encodedPassword != null
                && BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    }
}
