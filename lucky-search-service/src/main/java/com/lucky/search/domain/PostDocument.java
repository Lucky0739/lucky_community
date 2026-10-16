package com.lucky.search.domain;

import com.lucky.search.index.PostIndex;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

/**
 * 帖子搜索文档。字段名与 {@link PostIndex} 一致。
 */
@Data
@Document(indexName = PostIndex.NAME)
@Setting(settingPath = "elasticsearch/ik-synonym-settings.json")
public class PostDocument {

    @Id
    @Field(name = PostIndex.FIELD_POST_ID, type = FieldType.Long)
    private Long postId;

    @Field(name = PostIndex.FIELD_BAR_ID, type = FieldType.Long)
    private Long barId;

    @Field(name = PostIndex.FIELD_USER_ID, type = FieldType.Long)
    private Long userId;

    @Field(name = PostIndex.FIELD_TITLE, type = FieldType.Text,
            analyzer = PostIndex.ANALYZER, searchAnalyzer = PostIndex.SEARCH_ANALYZER)
    private String title;

    @Field(name = PostIndex.FIELD_CONTENT, type = FieldType.Text,
            analyzer = PostIndex.ANALYZER, searchAnalyzer = PostIndex.SEARCH_ANALYZER)
    private String content;

    @Field(name = PostIndex.FIELD_COVER_IMAGE, type = FieldType.Keyword, index = false)
    private String coverImage;

    @Field(name = PostIndex.FIELD_VISIBILITY_SCOPE, type = FieldType.Integer)
    private Integer visibilityScope;

    @Field(name = PostIndex.FIELD_DELETED, type = FieldType.Integer)
    private Integer deleted;

    @Field(name = PostIndex.FIELD_LIKE_COUNT, type = FieldType.Long)
    private Long likeCount;

    @Field(name = PostIndex.FIELD_CREATE_TIME, type = FieldType.Date,
            format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createTime;
}
