package com.lucky.community.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.lucky.community.vo.PostSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 帖子列表首屏的本机缓存（纯 Caffeine，不碰 Redis）
 *
 * <h3>缓存的为什么是「组装好的 VO」而不是「ID 列表」</h3>
 * 只缓存 ID 的话，回源仍要发 2 条批量关联查询（贴吧 / 作者），省下的只有主查询。
 * 而列表页的成本大头恰恰在那几条关联查询上，缓存 VO 才能把它们一起省掉，
 * 只剩 {@code fillSummaryInteraction} 的 5 次 Redis 调用。
 *
 * <h3>为什么可以不广播失效</h3>
 * TTL 只有秒级，多实例各自持有一份、靠自然过期收敛就够了。
 * 为 2 秒的新鲜度去搭 {@code PostCacheInvalidator} 那套跨实例广播是倒亏的——
 * 那套机制存在的理由是 {@code post:detail} 要维持分钟级的逻辑过期。
 *
 * <h3>与 {@code post:detail} 的关键差异</h3>
 * 详情缓存的条目里 {@code liked} / {@code favorited} 是<b>空的</b>，由 {@code getDetail}
 * 拷贝之后才填。本类沿用同一立场：缓存的 VO 里这几个字段恒为 null，
 * <b>绝不能</b>让某次请求填好的互动字段留进缓存，否则先访问的用户状态会泄漏给后面所有人。
 * 因此 {@link #getFirstPage} 在命中与未命中两条路径上都返回副本。
 */
@Slf4j
@Service
public class PostFeedCache {

    private final Cache<String, List<PostSummaryVO>> localCache;

    public PostFeedCache(@Qualifier("postFeedLocalCache") Cache<String, List<PostSummaryVO>> localCache) {
        this.localCache = localCache;
    }

    /**
     * 读首屏。命中与未命中都返回<b>副本</b>，调用方拿到的列表可以随便改，污染不到缓存里那份
     *
     * @param filterKey 筛选维度拼出的 key，见 {@link #filterKey}
     * @param loader    回源逻辑，返回组装好的<b>基础 VO</b>（互动字段留空），可为空列表
     */
    public List<PostSummaryVO> getFirstPage(String filterKey, Supplier<List<PostSummaryVO>> loader) {
        // Caffeine 的 get(key, mappingFunction) 自带本机单飞：同一个 key 并发未命中时
        // 只有第一个线程执行 loader，其余线程等待其结果（与 PostDetailCache 同一用法）。
        // 这挡不住跨实例的重复回源，但实例内不会出现「一个热点 key 失效就打 N 次库」
        List<PostSummaryVO> base = localCache.get(filterKey, k -> loader.get());
        return copy(base);
    }

    /**
     * 拼缓存 key：<b>请求的全部查询参数</b>，一个都不能少
     *
     * <p><b>{@code size} 必须在 key 里</b>。曾经漏掉过它：筛选维度相同的两个请求，
     * 先来的以 {@code size=5} 写入缓存，后来的 {@code size=19} 会直接命中并只拿到 5 条——
     * 返回条数与请求参数不符，且不报任何错。缓存 key 的判据是「这个值变不变会不影响响应内容」，
     * {@code size} 显然影响，就得进 key。
     *
     * <p><b>但不含调用者 userId</b>——互动字段（liked/favorited）是拷贝之后才填的，
     * 缓存内容对所有人一致。若哪天有人把 {@code UserContext.getUserId()} 加进 key，
     * 每个用户的浏览都会产生一份独立缓存，key 空间立刻爆炸且命中率趋近于零。
     * 这与 {@code PostQueryDTO.userId}（作者筛选，属于查询参数）是两回事，别混淆。
     */
    public static String filterKey(Long barId, Long userId, int size) {
        return (barId == null ? "" : barId) + ":" + (userId == null ? "" : userId) + ":" + size;
    }

    /** 逐条深拷贝。列表本身也新建，保证缓存里那份的双层结构都不被触碰 */
    private List<PostSummaryVO> copy(List<PostSummaryVO> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<PostSummaryVO> copy = new ArrayList<>(source.size());
        for (PostSummaryVO row : source) {
            PostSummaryVO one = new PostSummaryVO();
            BeanUtils.copyProperties(row, one);
            copy.add(one);
        }
        return copy;
    }
}
