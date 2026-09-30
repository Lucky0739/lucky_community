package com.lucky.common.auth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 双 Token 签发/校验工具
 */
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /** HS256 要求密钥不少于 256 位；过短的密钥可被暴力破解 */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret:}")
    private String secret;

    /** 短 token 有效期(秒) */
    @Getter
    @Value("${jwt.access-expire:1800}")
    private long accessExpire;

    /** 长 token 有效期(秒) */
    @Getter
    @Value("${jwt.refresh-expire:604800}")
    private long refreshExpire;

    /**
     * 轮换宽限期(秒)：旧 refresh 轮换后保留这段时间，用于吸收并发/重复刷新
     */
    @Getter
    @Value("${jwt.refresh-grace:60}")
    private long refreshGraceSeconds;

    /**
     * 启动即校验密钥：缺失或过短直接让应用起不来（fail-fast）
     **/
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 密钥未配置，应用拒绝启动。请设置环境变量 JWT_SECRET 后重启：\n"
                            + "  export JWT_SECRET=\"$(openssl rand -base64 48)\"");
        }
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 密钥过短：当前 " + len + " 字节，HS256 至少需要 " + MIN_SECRET_BYTES + " 字节。\n"
                            + "  export JWT_SECRET=\"$(openssl rand -base64 48)\"");
        }
    }

    /** 签/验共用密钥字节 */
    private byte[] key() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 签发一对双 Token
     */
    public IssuedTokens createTokenPair(Long userId) {
        Minted access = mint(userId, TYPE_ACCESS, accessExpire);
        Minted refresh = mint(userId, TYPE_REFRESH, refreshExpire);
        return new IssuedTokens(access.token(), refresh.token(), access.jti(), refresh.jti());
    }

    /**
     * 只签发一个 access token（不配套 refresh）
     */
    public IssuedAccess createAccessToken(Long userId) {
        Minted access = mint(userId, TYPE_ACCESS, accessExpire);
        return new IssuedAccess(access.token(), access.jti());
    }

    /**
     * 解析并校验 token（不查 Redis，只做签名/类型/时效校验）
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

    /** 签一个 token，并把 jti 一并带出来 */
    private Minted mint(Long userId, String type, long expireSeconds) {
        Date now = new Date();
        String jti = IdUtil.fastSimpleUUID();
        String token = JWT.create()
                // jti：唯一标识，用于会话管理/白名单
                .setJWTId(jti)
                .setPayload("userId", userId)
                .setPayload("type", type)
                .setIssuedAt(now)
                .setExpiresAt(new Date(now.getTime() + expireSeconds * 1000L))
                .setKey(key())
                .sign();
        return new Minted(token, jti);
    }

    /**
     * token 解析结果：身份 + 唯一标识
     */
    public record Payload(Long userId, String jti) {
    }

    /** 新签发的一对 token + 各自的 jti */
    public record IssuedTokens(String accessToken, String refreshToken, String accessJti, String refreshJti) {
    }

    /** 单个新签发的 access token + 其 jti */
    public record IssuedAccess(String token, String jti) {
    }

    /** 单个已签发 token 及其 jti */
    private record Minted(String token, String jti) {
    }
}
