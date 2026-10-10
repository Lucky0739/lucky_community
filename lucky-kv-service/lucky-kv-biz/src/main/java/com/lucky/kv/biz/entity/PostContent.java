package com.lucky.kv.biz.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(onConstructor_ = @PersistenceCreator)
@Table("post_content")
public class PostContent {

    /**
     * 帖子内容id
     */
    @PrimaryKey("id")
    private UUID id;

    /**
     * 帖子内容
     */
    private String content;
}
