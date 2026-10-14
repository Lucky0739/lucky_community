package com.lucky.kv.biz.repository;

import com.lucky.kv.biz.entity.CommentContent;
import com.lucky.kv.biz.entity.CommentContentPrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * 评论正文。主键 (noteId, yearMonth, contentId)：noteId + yearMonth 是分区键，contentId 是聚簇键。
 */
public interface CommentContentRepository extends CassandraRepository<CommentContent, CommentContentPrimaryKey> {

    /**
     * 按帖子批量读评论正文。noteId 定死分区，yearMonth / contentId 用 IN 收窄。
     * 显式 CQL 是为了让方法名短一点（派生查询名要写全 primaryKey 路径，太长）。
     */
    @Query("SELECT * FROM comment_content "
            + "WHERE note_id = :noteId "
            + "AND year_month IN (:yearMonths) "
            + "AND content_id IN (:contentIds)")
    List<CommentContent> findContents(@Param("noteId") Long noteId,
                                      @Param("yearMonths") List<String> yearMonths,
                                      @Param("contentIds") List<UUID> contentIds);
}
