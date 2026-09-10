package com.lucky.core.utils;

import cn.hutool.core.util.IdUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import com.lucky.core.dto.TokenDTO;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 双 Token 签发/校验工具
 * <ul>
 *   <li>access token：短时效，返回前端放内存，随请求头携带；可被拉黑踢下线</li>
 *   <li>refresh token：长时效，写入 HttpOnly Cookie；会话存 Redis，支持轮换/吊销</li>
 * </ul>
 * 每个 token 都带 jti(唯一标识)，用于黑名单与会话管理。
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    /** 短 token 有效期(秒) */
    @Getter
    @Value("${jwt.access-expire:1800}")
    private long accessExpire;

    /** 长 token 有效期(秒) */
    @Getter
    @Value("${jwt.refresh-expire:604800}")
    private long refreshExpire;

    /** 签/验共用密钥字节 */
    private byte[] key() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 签发 access token */
    public String createAccessToken(Long userId) {
        return createToken(userId, TYPE_ACCESS, accessExpire);
    }

    /** 签发 refresh token */
    public String createRefreshToken(Long userId) {
        return createToken(userId, TYPE_REFRESH, refreshExpire);
    }

    /**
     * 签发一对双 Token
     */
    public TokenDTO createTokenPair(Long userId) {
        return new TokenDTO(createAccessToken(userId), createRefreshToken(userId));
    }

    /**
     * 解析并校验 token（不查 Redis，只做签名/类型/时效校验）
     *
     * @param token        原始 token
     * @param expectedType access / refresh
     * @return 校验通过返回 userId + jti；否则返回 null
     */
    public Payload parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            // 1. 验签
            if (!JWTUtil.verify(token, key())) {
                return null;
            }
            JWT jwt = JWTUtil.parseToken(token);

            // 2. 类型校验：access 不能拿来换 token，refresh 也不能当接口凭证
            if (!expectedType.equals(String.valueOf(jwt.getPayload("type")))) {
                return null;
            }

            // 3. 时效校验：过期/签发时间异常会抛异常
            JWTValidator.of(jwt).validateDate();

            Object userId = jwt.getPayload("userId");
            Object jti = jwt.getPayload("jti");
            if (userId == null || jti == null) {
                return null;
            }
            return new Payload(Long.valueOf(userId.toString()), jti.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String createToken(Long userId, String type, long expireSeconds) {
        Date now = new Date();
        return JWT.create()
                // jti：唯一标识，用于黑名单/会话管理
                .setJWTId(IdUtil.fastSimpleUUID())
                .setPayload("userId", userId)
                .setPayload("type", type)
                .setIssuedAt(now)
                .setExpiresAt(new Date(now.getTime() + expireSeconds * 1000L))
                .setKey(key())
                .sign();
    }

    /**
     * token 解析结果：身份 + 唯一标识
     */
    public record Payload(Long userId, String jti) {
    }
}
