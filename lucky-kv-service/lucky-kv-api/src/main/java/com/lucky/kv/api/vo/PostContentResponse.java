package com.lucky.kv.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostContentResponse {

    /** 正文 UUID，写入 tb_post.content_uuid 并在 Cassandra 中作主键 */
    private UUID contentUuid;

    /**
     * 帖子内容
     */
    private String content;
}
