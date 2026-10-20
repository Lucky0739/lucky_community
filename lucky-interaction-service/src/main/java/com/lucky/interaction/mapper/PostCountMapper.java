package com.lucky.interaction.mapper;

import com.lucky.interaction.support.entity.PostCountSnapshot;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostCountMapper {

    /**
     * 写入点赞数。帖子还没有计数行时补一行，收藏数和评论数先写 0。
     */
    int upsertLikeCount(@Param("postId") Long postId, @Param("likeCount") long likeCount);

    int upsertFavoriteCount(@Param("postId") Long postId, @Param("favoriteCount") long favoriteCount);

    /** 帖子 id 升序，取游标之后的一页。afterId 不含。 */
    List<Long> selectPostIdsAfter(@Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * 对账 (afterId, untilId] 这一段帖子。计数来自 tb_thumb、tb_favorite、未删除的 tb_comment。
     * 只返回本段里有变化的行。
     */
    List<PostCountSnapshot> reconcilePostRange(@Param("afterId") long afterId, @Param("untilId") long untilId);
}
