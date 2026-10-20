package com.lucky.interaction.mapper;

import org.apache.ibatis.annotations.Param;

public interface UserInfoStatMapper {

    /** 关注人的关注数 +1 */
    int increaseFollowee(@Param("userId") Long userId);

    /** 关注人的关注数 -1，不会减成负数 */
    int decreaseFollowee(@Param("userId") Long userId);

    /** 被关注人粉丝 +1 */
    int increaseFans(@Param("userId") Long userId);

    /** 被关注人粉丝 -1，不会减成负数 */
    int decreaseFans(@Param("userId") Long userId);
}
