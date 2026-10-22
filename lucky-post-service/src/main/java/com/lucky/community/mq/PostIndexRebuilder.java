package com.lucky.community.mq;

import com.lucky.community.entity.Post;
import com.lucky.community.mapper.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 索引全量重建：把现有的「公开可见」帖子整体重投一遍索引事件。
 *
 * <p>两个用途：
 * <ul>
 *   <li>本次上线时把历史帖子补进 ES（索引此前一直是空的）</li>
 *   <li>事后对账 —— 增量同步漏了、兜底表也没救回来时，重建是最终的兜底手段</li>
 * </ul>
 *
 * <p>正文不在 PG 里，{@link PostIndexEmitter#emit} 会按需从 Cassandra 读，
 * 所以这是一轮"全表扫 + 逐条 KV 读 + 逐条投递"的重活，必须异步跑，不能挂在 HTTP 请求上。
 */
@Slf4j
@Service
public class PostIndexRebuilder {

    /** 每批从 PG 取多少条。批大一点减少往返，但别大到把消息堆积起来 */
    private static final int BATCH = 200;

    private final PostMapper postMapper;
    private final PostIndexEmitter postIndexEmitter;
    private final Executor executor;

    public PostIndexRebuilder(PostMapper postMapper,
                              PostIndexEmitter postIndexEmitter,
                              @Qualifier("postIndexRebuildExecutor") Executor executor) {
        this.postMapper = postMapper;
        this.postIndexEmitter = postIndexEmitter;
        this.executor = executor;
    }

    /**
     * 异步触发一轮重建，立刻返回。
     *
     * @throws RejectedExecutionException 已经有一轮在跑（或刚跑完还在队列里）
     */
    public void triggerAsync() {
        executor.execute(this::rebuild);
    }

    private void rebuild() {
        long delivered = 0;
        long lastId = 0L;
        try {
            while (true) {
                List<Post> batch = postMapper.selectApprovedAfterId(lastId, BATCH);
                if (batch.isEmpty()) {
                    break;
                }
                for (Post post : batch) {
                    // 正文传 null：走 KV 回读。这批帖不在请求上下文里，没有现成正文
                    postIndexEmitter.emit(post.getId(), null);
                    lastId = post.getId();
                }
                delivered += batch.size();
                log.info("索引重建进行中，已投递 {} 条（lastId={}）", delivered, lastId);
            }
            log.info("索引重建完成，共投递 {} 条", delivered);
        } catch (Exception e) {
            // 中断了就中断了：已投递的部分已经生效，重新触发一轮即可（写入按 postId 幂等）
            log.error("索引重建中断，已投递 {} 条（lastId={}），可重新触发", delivered, lastId, e);
        }
    }
}
