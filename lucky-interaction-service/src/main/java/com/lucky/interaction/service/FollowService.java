package com.lucky.interaction.service;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.FollowListQueryDTO;
import com.lucky.interaction.vo.FollowUserVO;

public interface FollowService {

    /**
     * 关注用户：先确认对方存在，再 Lua 原子写入 Redis，成功后投递 MQ 落库
     */
    void follow(Long userId, Long followUserId);

    /**
     * 当前用户是否已关注对方。热路径看 Redis，ZSet 不存在时回源 tb_follow。
     */
    boolean isFollowed(Long userId, Long followUserId);

    /**
     * 取消关注：Lua 原子删 Redis，成功后投递 MQ 删 tb_follow
     */
    void unfollow(Long userId, Long followUserId);

    /**
     * 关注列表。可按被关注用户昵称关键词分页。每条带当前登录用户对该用户的关注状态，便于列表里直接取消关注
     */
    PageResult<FollowUserVO> pageFollowees(Long viewerId, FollowListQueryDTO query);
}
