package com.lucky.community.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.lucky.community.vo.BarVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 贴吧列表的本机缓存（纯 Caffeine，不碰 Redis）
 *
 * <h3>只缓存什么</h3>
 * 无关键词的<strong>总数</strong>，以及无关键词的<strong>第一页</strong>。
 * 列表首页是读热点：高并发下同一条 {@code COUNT + OFFSET 0} 会被打成风暴。
 * 翻页的 {@code pageNum × pageSize} 组合接近无穷，缓存命中率趋近于零，只会白占内存。
 *
 * <h3>为什么不缓存 keyword 搜索</h3>
 * 关键词空间无穷。把搜索结果放进来，恶意请求用随机前缀就能把 Caffeine 打成垃圾，
 * 还会挤掉真正的首页缓存。搜索本身走 {@code name LIKE prefix%}，有唯一索引托底，
 * 不是这个热点。
 *
 * <h3>为什么可以不广播失效</h3>
 * TTL 只有秒级，多实例各自持有一份、靠自然过期收敛就够了。
 * 写路径会本机 {@link #invalidate()}，当前实例立刻看到新吧；其他实例最多旧一个 TTL。
 * 为几秒新鲜度去搭帖子详情那套跨实例广播是倒亏的。
 *
 * <h3>本机单飞</h3>
 * Caffeine 的 {@code get(key, mappingFunction)} 在同一个 key 并发未命中时
 * 只有第一个线程执行 loader，其余线程等待其结果。这挡不住跨实例的重复回源，
 * 但实例内不会出现「一个热点 key 失效就打 N 次库」。
 *
 * <h3>空结果也缓存</h3>
 * 库里一个吧都没有时，{@code COUNT} 仍然会被打。把 {@code 0} 和空列表放进缓存，
 * 才挡得住对空目录的穿透。
 */
@Slf4j
@Service
public class BarPageCache {

    static final String TOTAL_KEY = "total";

    private final Cache<String, List<BarVO>> pageCache;
    private final Cache<String, Long> countCache;

    public BarPageCache(@Qualifier("barPageLocalCache") Cache<String, List<BarVO>> pageCache,
                        @Qualifier("barCountLocalCache") Cache<String, Long> countCache) {
        this.pageCache = pageCache;
        this.countCache = countCache;
    }

    /**
     * 读无关键词第一页。命中与未命中都返回<b>副本</b>，调用方可以改列表，污染不到缓存里那份。
     *
     * @param pageSize 必须进 key。漏掉的话，先来的 {@code size=5} 写入后，
     *                 {@code size=20} 会命中并只拿到 5 条——返回条数与请求不符且不报错
     * @param loader   回源逻辑，可返回空列表
     */
    public List<BarVO> getFirstPage(int pageSize, Supplier<List<BarVO>> loader) {
        List<BarVO> cached = pageCache.get(firstPageKey(pageSize), k -> {
            List<BarVO> fresh = loader.get();
            return fresh == null ? List.of() : fresh;
        });
        return copy(cached);
    }

    /**
     * 读无关键词的吧总数。空目录（{@code 0}）也缓存，避免 COUNT 被打穿
     */
    public long getTotal(Supplier<Long> loader) {
        Long cached = countCache.get(TOTAL_KEY, k -> {
            Long fresh = loader.get();
            return fresh == null ? 0L : fresh;
        });
        return cached == null ? 0L : cached;
    }

    /**
     * 写路径调用：建吧 / 改吧 / 停用吧之后，本机列表立刻失效。
     * 其他实例靠 TTL 收敛，不在这里广播。
     */
    public void invalidate() {
        pageCache.invalidateAll();
        countCache.invalidateAll();
    }

    static String firstPageKey(int pageSize) {
        return "first:" + pageSize;
    }

    private List<BarVO> copy(List<BarVO> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<BarVO> copy = new ArrayList<>(source.size());
        for (BarVO row : source) {
            BarVO one = new BarVO();
            BeanUtils.copyProperties(row, one);
            copy.add(one);
        }
        return copy;
    }
}
