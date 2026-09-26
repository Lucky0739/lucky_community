package com.lucky.core.bar.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.redis.RedisKeyManage;
import com.lucky.core.bar.vo.BarVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 贴吧详情的缓存：{@code bar:detail:{barId}} -> {@link BarVO} 的 JSON
 */
@Slf4j
@Service
public class BarDetailCache {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper cacheObjectMapper;

    private final long ttlSeconds;

    public BarDetailCache(StringRedisTemplate stringRedisTemplate,
                          @Qualifier("cacheObjectMapper") ObjectMapper cacheObjectMapper,
                          @Value("${bar.cache.ttl-seconds:1800}") long ttlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheObjectMapper = cacheObjectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * 读：缓存命中直接返回，未命中调 loader 回源并回填
     */
    public BarVO get(Long barId, Function<Long, BarVO> loader) {
        BarVO cached = getFromCache(barId);
        if (cached != null) {
            return cached;
        }
        BarVO fresh = loader.apply(barId);
        // 查不到不缓存：这是防穿透的常见手段，但本项目没有吧的布隆过滤器，
        // 而「查询不存在的吧」本身是低频异常路径，不值得为它多存一份空值
        if (fresh != null) {
            put(fresh);
        }
        return fresh;
    }

    /**
     * 写入缓存。供预热或已知数据变更后主动刷新使用
     */
    public void put(BarVO vo) {
        if (vo == null || vo.getBarId() == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyManage.BAR_DETAIL.key(vo.getBarId()),
                    cacheObjectMapper.writeValueAsString(vo),
                    ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入贴吧详情缓存失败，barId={}", vo.getBarId(), e);
        }
    }

    /**
     * 失效缓存：吧信息被修改时调用
     */
    public void evict(Long barId) {
        if (barId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(RedisKeyManage.BAR_DETAIL.key(barId));
        } catch (Exception e) {
            log.warn("删除贴吧详情缓存失败，barId={}", barId, e);
        }
    }

    private BarVO getFromCache(Long barId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyManage.BAR_DETAIL.key(barId));
            return json == null ? null : cacheObjectMapper.readValue(json, BarVO.class);
        } catch (Exception e) {
            // 缓存挂了、或者条目格式不兼容（VO 改过字段）都降级回源，不能把读接口带挂
            log.warn("读取贴吧详情缓存失败，降级回源，barId={}", barId, e);
            return null;
        }
    }
}
