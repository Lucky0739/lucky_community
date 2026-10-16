package com.lucky.interaction.mapper;

import com.lucky.interaction.entity.Favorite;
import com.lucky.interaction.entity.UserPostInteraction;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FavoriteMapper {

    long countByPostId(@Param("postId") Long postId);

    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);

    boolean exists(@Param("userId") Long userId, @Param("postId") Long postId);

    List<Favorite> batchInsertIgnore(@Param("list") List<Favorite> list);

    List<Favorite> batchDeleteByUserAndPost(@Param("list") List<Favorite> list);

    long countVisibleByUserId(@Param("userId") Long userId);

    List<UserPostInteraction> selectVisiblePageByUserId(@Param("userId") Long userId,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);

    List<Long> listFavoritedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
