package com.lucky.search.domain;

import com.lucky.search.index.BarIndex;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * 贴吧搜索文档。字段名与 {@link BarIndex} 一致。
 */
@Data
@Document(indexName = BarIndex.NAME)
@Setting(settingPath = "elasticsearch/ik-synonym-settings.json")
public class BarDocument {

    @Id
    @Field(name = BarIndex.FIELD_BAR_ID, type = FieldType.Long)
    private Long barId;

    @Field(name = BarIndex.FIELD_NAME, type = FieldType.Text,
            analyzer = BarIndex.ANALYZER, searchAnalyzer = BarIndex.SEARCH_ANALYZER)
    private String name;

    @Field(name = BarIndex.FIELD_AVATAR, type = FieldType.Keyword, index = false)
    private String avatar;

    @Field(name = BarIndex.FIELD_FOLLOW_COUNT, type = FieldType.Integer)
    private Integer followCount;

    @Field(name = BarIndex.FIELD_POST_COUNT, type = FieldType.Integer)
    private Integer postCount;

    @Field(name = BarIndex.FIELD_STATUS, type = FieldType.Integer)
    private Integer status;
}
