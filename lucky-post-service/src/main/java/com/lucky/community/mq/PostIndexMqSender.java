package com.lucky.community.mq;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lucky.common.constant.PostIndexMqConstant;
import com.lucky.community.entity.PostIndexOutbox;
import com.lucky.community.mapper.PostIndexOutboxMapper;
import com.lucky.common.mq.PostIndexMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 同步发帖子索引变更消息。失败后等 1 秒、2 秒、4 秒再试，一共再试 3 次；
 * 还是失败就写兜底表，由 {@link PostIndexOutboxJob} 以后补发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostIndexMqSender {

    /**
     * 自己 new 而不注入容器里的 ObjectMapper：本服务那个 bean 是 {@code cacheObjectMapper}
     * （见 CacheConfig），格式跟着缓存走，拿来序列化消息会把两边约定搞乱。
     */
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final RocketMQTemplate rocketMQTemplate;
    private final PostIndexOutboxMapper postIndexOutboxMapper;

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void send(PostIndexMessage message) throws JsonProcessingException {
        String json = JSON.writeValueAsString(message);
        rocketMQTemplate.syncSend(
                PostIndexMqConstant.MQ_DESTINATION,
                MessageBuilder.withPayload(json).build());
    }

    @Recover
    public void saveOutbox(Exception exception, PostIndexMessage message) throws JsonProcessingException {
        log.error("帖子索引消息发送多次失败，先写入兜底表，postId={}", message.getPostId(), exception);
        PostIndexOutbox row = new PostIndexOutbox();
        row.setId(IdUtil.getSnowflakeNextId());
        row.setTopic(PostIndexMqConstant.MQ_DESTINATION);
        row.setPayload(JSON.writeValueAsString(message));
        postIndexOutboxMapper.insert(row);
    }
}
