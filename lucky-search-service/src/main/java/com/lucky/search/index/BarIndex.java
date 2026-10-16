package com.lucky.search.index;

/**
 * 贴吧索引。搜索只匹配吧名。写入用 ik_max_word，查询用带同义词的 ik_smart_synonym。
 */
public class BarIndex {

    private BarIndex() {
    }

    public static final String NAME = "bar";

    public static final String ANALYZER = "ik_max_word";

    public static final String SEARCH_ANALYZER = "ik_smart_synonym";

    /** tb_bar.id */
    public static final String FIELD_BAR_ID = "id";

    /** tb_bar.name，搜索字段 */
    public static final String FIELD_NAME = "name";

    /** tb_bar.avatar */
    public static final String FIELD_AVATAR = "avatar";

    /** tb_bar.follow_count */
    public static final String FIELD_FOLLOW_COUNT = "follow_count";

    /** tb_bar.post_count */
    public static final String FIELD_POST_COUNT = "post_count";

    /** tb_bar.status。1 正常，0 停用 */
    public static final String FIELD_STATUS = "status";
}
