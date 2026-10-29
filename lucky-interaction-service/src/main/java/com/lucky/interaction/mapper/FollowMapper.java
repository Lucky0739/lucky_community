package com.lucky.interaction.mapper;

import com.lucky.interaction.entity.Follow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FollowMapper {

    /**
     * 幂等插入。唯一冲突时返回 0，供 MQ 重试时跳过计数累加
     */
    int insertIgnore(Follow follow);

    boolean exists(@Param("userId") Long userId, @Param("barId") Long barId);

    int deleteByUserAndBar(@Param("userId") Long userId, @Param("barId") Long barId);

    long countByUserId(@Param("userId") Long userId, @Param("keyword") String keyword);

    List<Follow> selectPageByUserId(@Param("userId") Long userId,
                                    @Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    List<Long> selectFollowedBarIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    List<Long> selectUserIdsByBarId(@Param("barId") Long barId);

    List<Long> selectBarIdsByUserId(@Param("userId") Long userId);

    long countByBarId(@Param("barId") Long barId);

    List<Follow> selectPageByBarId(@Param("barId") Long barId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);
}
