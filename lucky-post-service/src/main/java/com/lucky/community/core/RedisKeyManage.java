package com.lucky.community.core;

import lombok.Getter;

/**
 * Redis key 统一管理：集中定义 key 模板，避免各处硬编码拼接
 * <p>
 * 用法：{@code RedisKeyManage.REFRESH_SESSION.key(refreshJti)}
 */
@Getter
public enum RedisKeyManage {

    /**
     * 用户已签发 access 的 jti 白名单：login:access:user:{userId} -> Set(accessJti)
     * <p>TTL = access 有效期。校验走 SISMEMBER，吊销只需 DEL 该 key(该用户全部 access 瞬时失效)
     */
    ACCESS_USER("login:access:user:%s"),

    /** refresh 会话：login:refresh:{jti} -> userId，TTL = refresh 有效期 */
    REFRESH_SESSION("login:refresh:%s"),

    /** 用户全部 refresh 会话集合：login:refresh:user:{userId} -> Set(jti) */
    REFRESH_USER("login:refresh:user:%s"),

    /**
     * 轮换宽限记录：login:rotated:{oldJti} -> 新 refresh 的 jti，TTL = 宽限期
     */
    ROTATED("login:rotated:%s");

    /**
     * key 模板，如 login:refresh:%s
     */
    private final String pattern;

    RedisKeyManage(String pattern) {
        this.pattern = pattern;
    }

    /** 拼接出最终 key */
    public String key(Object... args) {
        return String.format(pattern, args);
    }
}
