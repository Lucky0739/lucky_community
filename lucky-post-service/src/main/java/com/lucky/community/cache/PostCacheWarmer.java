package com.lucky.community.cache;

import com.lucky.community.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时预热热点帖子缓存
 *
 * <h3>为什么这里用 ApplicationRunner，而布隆过滤器用的是 {@code @Bean(initMethod)}</h3>
 * 两者恰好相反，因为<b>晚一步的代价不同</b>：
 * <table>
 *   <tr><th></th><th>布隆过滤器</th><th>缓存预热</th></tr>
 *   <tr><td>晚一步的后果</td><td>真实存在的帖子 <b>404</b>（正确性问题）</td>
 *       <td>请求多查一次库（<b>性能问题</b>）</td></tr>
 *   <tr><td>所以</td><td>必须赶在 Tomcat 接收流量之前完成</td>
 *       <td>跑在启动之后即可，不必阻塞启动</td></tr>
 * </table>
 * Spring Boot 在 {@code finishRefresh()} 里启动 Tomcat 连接器，<b>早于</b> {@code ApplicationRunner}
 * 回调——这个窗口对布隆过滤器是致命的，对缓存预热则只是少省几次回源。见
 * {@code PostBloomFilterConfig} 里同样的判断。
 *
 * <h3>为什么只预热「最新 N 帖」</h3>
 * 项目里没有访问计数器，用发布时间代替热度：雪花 ID 时间有序，{@code ORDER BY id DESC}
 * 直接命中主键，新帖也确实是社区里最常被点开的。等有了真实访问量统计再换成按热度选。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCacheWarmer implements ApplicationRunner {

    private final PostService postService;

    /** 预热条数，0 表示关闭 */
    @Value("${post.cache.warm-up-count:50}")
    private int warmUpCount;

    @Override
    public void run(ApplicationArguments args) {
        if (warmUpCount <= 0) {
            log.info("帖子缓存预热已关闭，warm-up-count={}", warmUpCount);
            return;
        }
        try {
            long startedAt = System.currentTimeMillis();
            int count = postService.warmUpRecentPosts(warmUpCount);
            log.info("帖子缓存启动预热完成，预热 {} 帖，耗时 {}ms", count, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            // 预热失败只是少省几次回源，不该让应用起不来——与布隆过滤器启动失败必须中断的情况不同
            log.warn("帖子缓存启动预热失败，将按需回源", e);
        }
    }
}
