package com.lucky.interaction.service;

public interface FollowService {

    /**
     * 关注用户：先确认对方存在，再 Lua 原子写入 Redis，成功后投递 MQ 落库
     */
    void follow(Long userId, Long followUserId);
}
