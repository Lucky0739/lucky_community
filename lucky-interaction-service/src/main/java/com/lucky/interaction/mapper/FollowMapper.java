package com.lucky.interaction.mapper;

import com.lucky.interaction.entity.Follow;

public interface FollowMapper {

    /**
     * 幂等插入。唯一冲突时返回 0，供 MQ 重试时跳过计数累加
     */
    int insertIgnore(Follow follow);
}
