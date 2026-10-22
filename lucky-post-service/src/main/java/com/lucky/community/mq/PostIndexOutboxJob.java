package com.lucky.community.mq;

import com.lucky.common.constant.PostIndexMqConstant;
import com.lucky.community.entity.PostIndexOutbox;
import com.lucky.community.mapper.PostIndexOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把发送失败的帖子索引消息再发出去，成功后删掉兜底记录。
 *
 * <p>消息内容里的字段是当时算好的（尤其 {@code visible}），补发出去可能已经过期 ——
 * 但无关紧要：索引是派生数据，真实的兜底是 {@code POST /admin/post/reindex} 全量重建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostIndexOutboxJob {

    private final PostIndexOutboxMapper postIndexOutboxMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelay = 5000)
    public void resend() {
        List<PostIndexOutbox> rows = postIndexOutboxMapper.selectOldest(PostIndexMqConstant.OUTBOX_BATCH);
        for (PostIndexOutbox row : rows) {
            try {
                rocketMQTemplate.syncSend(row.getTopic(),
                        MessageBuilder.withPayload(row.getPayload()).build());
                postIndexOutboxMapper.deleteById(row.getId());
            } catch (RuntimeException e) {
                // 留到下一轮再试，整批不中断
                log.warn("帖子索引兜底消息补发失败，下次再试，id={}", row.getId(), e);
            }
        }
    }
}
