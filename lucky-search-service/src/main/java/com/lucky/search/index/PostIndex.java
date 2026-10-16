package com.lucky.search.index;

/**
 * 帖子索引。搜索只匹配标题和正文。写入用 ik_max_word，查询用带同义词的 ik_smart_synonym。
 */
public class PostIndex {

    private PostIndex() {
    }

    public static final String NAME = "post";

    public static final String ANALYZER = "ik_max_word";

    public static final String SEARCH_ANALYZER = "ik_smart_synonym";

    /** tb_post.id */
    public static final String FIELD_POST_ID = "id";

    /** tb_post.bar_id */
    public static final String FIELD_BAR_ID = "bar_id";

    /** tb_post.user_id */
    public static final String FIELD_USER_ID = "user_id";

    /** tb_post.title，搜索字段 */
    public static final String FIELD_TITLE = "title";

    /** 帖子正文，搜索字段。来自 Cassandra，不进 PostgreSQL 正文列 */
    public static final String FIELD_CONTENT = "content";

    /** tb_post.cover_image */
    public static final String FIELD_COVER_IMAGE = "cover_image";

    /** tb_post.visibility_scope */
    public static final String FIELD_VISIBILITY_SCOPE = "visibility_scope";

    /** tb_post.deleted */
    public static final String FIELD_DELETED = "deleted";

    /** tb_post_count.like_count */
    public static final String FIELD_LIKE_COUNT = "like_count";

    /** tb_post.create_time */
    public static final String FIELD_CREATE_TIME = "create_time";
}
