package com.lucky.core.auth;

import com.lucky.redis.RedisKeyManage;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Token 的 Redis 存储：refresh 会话 + access 白名单（踢下线）+ 轮换宽限记录
 * key 模板统一由 {@link RedisKeyManage} 管理
 * <p>
 * 为什么用「白名单」而不是「黑名单」：黑名单只能吊销调用方<b>已经知道 jti</b> 的 token，
 * 而踢下线/复用检测等场景手上只有 userId，补丁贴不上去（这正是旧实现里只有登出能生效的原因）。
 * 白名单记录「该用户当前签发过哪些 access」，吊销只需 DEL 集合，O(1) 且无需知道任何 jti。
 * 代价是失败方向：白名单丢失 = 全员登出（fail-closed），黑名单丢失 = 已吊销 token 复活（fail-open）。
 * 安全场景选 fail-closed。
 */
@Service
public class TokenRedisService {

    /**
     * 轮换抢占的占位值：表示「某个请求正在轮换这个 jti，结果还没写好」
     * <p>它是哨兵值，绝不是 jti，读取方必须先判掉再当 jti 用
     */
    public static final String ROTATION_PENDING = "PENDING";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ---------------------------------------------------------------- access 白名单

    /**
     * 登记一个已签发的 access jti（加入白名单）
     * <p>TTL 设为 access 有效期。注意这里每次都刷新 TTL，理论上会让旧 jti 最多多留一个
     * access 有效期；但 token 自身的 exp 才是有效性的最终依据（见 JwtUtil#parse 的时效校验），
     * 所以多留的那段是不可达的，仅为内存占用，无需额外清理。
     */
    public void whitelistAccess(Long userId, String accessJti, long accessExpireSeconds) {
        if (accessJti == null) {
            return;
        }
        String key = RedisKeyManage.ACCESS_USER.key(userId);
        stringRedisTemplate.opsForSet().add(key, accessJti);
        stringRedisTemplate.expire(key, accessExpireSeconds, TimeUnit.SECONDS);
    }
    /** 该 access 是否仍在该用户的白名单里（不在 = 未签发/已过期清理/已被踢下线） */
    public boolean isAccessWhitelisted(Long userId, String accessJti) {
        return accessJti != null && Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                        .isMember(RedisKeyManage.ACCESS_USER.key(userId), accessJti));
    }

    //refresh 会话

    /** 登录/续期后：登记 refresh 会话（jti -> userId），并纳入该用户会话集合 */
    public void saveRefreshSession(Long userId, String refreshJti, long refreshExpireSeconds) {
        stringRedisTemplate.opsForValue().set(RedisKeyManage.REFRESH_SESSION.key(refreshJti),
                String.valueOf(userId), refreshExpireSeconds, TimeUnit.SECONDS);
        String userKey = RedisKeyManage.REFRESH_USER.key(userId);
        stringRedisTemplate.opsForSet().add(userKey, refreshJti);
        stringRedisTemplate.expire(userKey, refreshExpireSeconds, TimeUnit.SECONDS);
    }

    /** refresh 会话是否存在且属于该用户 */
    public boolean isRefreshSessionValid(Long userId, String refreshJti) {
        String v = stringRedisTemplate.opsForValue().get(RedisKeyManage.REFRESH_SESSION.key(refreshJti));
        return v != null && v.equals(String.valueOf(userId));
    }

    /** 删除单个 refresh 会话（轮换时删旧；登出时删当前） */
    public void removeRefreshSession(Long userId, String refreshJti) {
        stringRedisTemplate.delete(RedisKeyManage.REFRESH_SESSION.key(refreshJti));
        stringRedisTemplate.opsForSet().remove(RedisKeyManage.REFRESH_USER.key(userId), refreshJti);
    }

    /**
     * 原子抢占旧 jti 的轮换权（SET NX）
     * 并发场景下两个请求都会先通过 {@link #isRefreshSessionValid}，若不做抢占就会各自轮换，
     * 产出两个孤儿会话。抢占保证只有一个请求能真正轮换。
     *
     * @return true = 抢占成功，由本请求执行轮换；false = 别人正在轮换，走等待分支
     */
    public boolean tryClaimRotation(String oldJti, long graceSeconds) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyManage.ROTATED.key(oldJti), ROTATION_PENDING,
                graceSeconds, TimeUnit.SECONDS));
    }

    /** 轮换完成：把占位值改写成新会话的 jti，供并发方复用 */
    public void completeRotation(String oldJti, String newRefreshJti, long graceSeconds) {
        stringRedisTemplate.opsForValue().set(RedisKeyManage.ROTATED.key(oldJti),
                newRefreshJti, graceSeconds, TimeUnit.SECONDS);
    }

    /**
     * 读轮换记录，原样返回（可能是 {@link #ROTATION_PENDING} 哨兵，也可能是新 jti）
     * 调用方必须先判掉哨兵再当 jti 使用
     */
    public String getRotation(String oldJti) {
        return stringRedisTemplate.opsForValue().get(RedisKeyManage.ROTATED.key(oldJti));
    }

    /**
     * 踢下线 / 登出：
     * 1) DEL 该用户的 access 白名单 → 已签发的 access 全部瞬时失效（无需知道任何 jti）
     * 2) 清空该用户全部 refresh 会话 → 断掉「用 refresh 换新 access」的退路
     * <p>不需要清理轮换宽限记录：宽限路径复用前会校验新会话是否仍存活，
     * 这里把会话删掉，宽限记录自然失效，避免了两者之间的时序竞态。
     *
     * <p><b>本方法是「清空当前会话」，不是「禁止该用户再持有会话」</b>——登出后重新登录
     * 应该能成功。要禁止，用 {@link #revokeUser(Long, long)}
     */
    public void kickOut(Long userId) {
        stringRedisTemplate.delete(RedisKeyManage.ACCESS_USER.key(userId));

        String userKey = RedisKeyManage.REFRESH_USER.key(userId);
        Set<String> jtis = stringRedisTemplate.opsForSet().members(userKey);
        if (jtis != null) {
            jtis.forEach(jti -> stringRedisTemplate.delete(RedisKeyManage.REFRESH_SESSION.key(jti)));
        }
        stringRedisTemplate.delete(userKey);
    }

    // ---------------------------------------------------------------- 撤销

    /**
     * 撤销该用户：写墓碑 + 清空现有会话。用于用户被删除/封禁等「不再允许持有会话」的场景
     *
     * <p><b>为什么不能只调 {@link #kickOut}</b>：kickOut 只是 DEL 会话键，而签发路径
     * ({@code TokenIssuer#issuePair} / {@code TokenIssuer#issueAccessOnly}) 会把它们重新写回来。
     * 一条恰好在删除瞬间处于「已过会话校验、未写白名单」之间的刷新请求，就能把刚删掉的键
     * 原样重建，被删用户于是拿到有效 token 并可持续续期——实测并发刷新下 12/12 复现。
     * 墓碑（{@link RedisKeyManage#REVOKED_USER}）只有本方法写、签发路径从不触碰，
     * 鉴权读到即拒绝，撤销这才不依赖时序
     *
     * @param ttlSeconds 墓碑存活时间，须 ≥ 该 userId 任何 token 的最长剩余寿命
     *                   （取 refresh 有效期即可），到期后墓碑自动清理
     */
    public void revokeUser(Long userId, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(RedisKeyManage.REVOKED_USER.key(userId), "1",
                ttlSeconds, TimeUnit.SECONDS);
        kickOut(userId);
    }

    /** 该用户是否已被撤销（墓碑存在） */
    public boolean isUserRevoked(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeyManage.REVOKED_USER.key(userId)));
    }
}
