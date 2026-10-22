package com.lucky.community.mapper;

import com.lucky.community.entity.Post;
import com.lucky.community.support.entity.BarPostCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostMapper {

    int insert(Post post);

    Post selectById(@Param("id") Long id);

    /** 审核结果回写前锁定帖子行，和改帖事务串行化，见 AiModerationService */
    Post selectByIdForUpdate(@Param("id") Long id);

    int updateContent(Post post);

    /** 正文投影到 KV 成功后回写 Cassandra 主键 */
    int updateContentUuid(@Param("postId") Long postId, @Param("contentUuid") String contentUuid);

    int resetAnalysisOnEdit(@Param("id") Long id);

    int logicalDeleteById(@Param("id") Long id);

    /**
     * 一次写齐 AI 管道的三个状态列。三者独立，调用方按当次 /govern 的结局取值。
     * {@code analysisTime} 只在该次分析成功时给，其余传 null。
     */
    int updateAiStatus(@Param("id") Long id,
                       @Param("eventStatus") int eventStatus,
                       @Param("embedStatus") int embedStatus,
                       @Param("analysisStatus") int analysisStatus,
                       @Param("analysisTime") LocalDateTime analysisTime);

    /**
     * 捞出需要重试 AI 审核的帖子：终态失败（分析失败 / 向量写入失败 / 事件投递失败），
     * 且自动重试次数还没到上限。走 idx_post_analysis_status 与 idx_post_embed_status。
     */
    List<Post> selectRetryable(@Param("limit") int limit, @Param("maxRetry") int maxRetry);

    /** 自动重试次数自增。人工手动重跑时会清零，所以这里只做 +1 */
    int incrementAnalysisRetry(@Param("id") Long id);

    /** 人工重跑前清零计数，让自动重试重新接管这条帖 */
    int resetAnalysisRetry(@Param("id") Long id);

    List<Post> selectApprovedFeed(@Param("barId") Long barId,
                                  @Param("userId") Long userId,
                                  @Param("lastId") Long lastId,
                                  @Param("lastScore") Long lastScore,
                                  @Param("likeWeight") int likeWeight,
                                  @Param("favoriteWeight") int favoriteWeight,
                                  @Param("limit") int limit);

    /**
     * 作者看自己主页：含审核中/审核失败等未对外公开的帖，仍排除 deleted。
     */
    List<Post> selectAuthorFeed(@Param("barId") Long barId,
                                @Param("userId") Long userId,
                                @Param("lastId") Long lastId,
                                @Param("lastScore") Long lastScore,
                                @Param("likeWeight") int likeWeight,
                                @Param("favoriteWeight") int favoriteWeight,
                                @Param("limit") int limit);

    List<Post> selectApprovedRecent(@Param("limit") int limit);

    /**
     * 按 id 升序翻页取「公开可见」的帖子，供索引全量重建用。
     * 用 keyset（{@code id > lastId}）而不是 OFFSET：重建是边扫边投递，OFFSET 会越翻越慢。
     */
    List<Post> selectApprovedAfterId(@Param("lastId") Long lastId, @Param("limit") int limit);

    List<Long> selectAllIds();

    List<Post> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 批量取公开可见的帖子。相似帖是历史 AI 结论，
     * 读取时必须重新过一遍可见性，过滤已删除/重新审核/未通过的帖。
     */
    List<Post> selectApprovedByIds(@Param("ids") List<Long> ids);

    long countAdminPage(@Param("barId") Long barId,
                        @Param("pending") Boolean pending,
                        @Param("analysisFailed") Boolean analysisFailed);

    List<Post> selectAdminPage(@Param("barId") Long barId,
                               @Param("pending") Boolean pending,
                               @Param("analysisFailed") Boolean analysisFailed,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    List<Post> selectLatestApprovedByBarIds(@Param("barIds") List<Long> barIds);

    long countNotDeletedByBarId(@Param("barId") Long barId);

    List<BarPostCountRow> countNotDeletedByBarIds(@Param("barIds") List<Long> barIds);
}
