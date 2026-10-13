package com.lucky.comment.service.impl;

import com.lucky.comment.constant.CommentMqConstant;
import com.lucky.comment.mq.PublishCommentMessage;
import com.lucky.comment.service.CommentHeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentHeatServiceImpl implements CommentHeatService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void add(List<PublishCommentMessage> freshComments) {
        if (freshComments == null || freshComments.isEmpty()) {
            return;
        }
        Map<Long, Integer> byBar = new HashMap<>();
        for (PublishCommentMessage message : freshComments) {
            if (message.getBarId() != null) {
                byBar.merge(message.getBarId(), 1, Integer::sum);
            }
        }
        byBar.forEach((barId, count) -> {
            try {
                stringRedisTemplate.opsForZSet().incrementScore(
                        CommentMqConstant.BAR_HOT_KEY, String.valueOf(barId), count);
            } catch (RuntimeException e) {
                log.warn("评论增加贴吧热度失败，barId={}，count={}", barId, count, e);
            }
        });
    }
}
