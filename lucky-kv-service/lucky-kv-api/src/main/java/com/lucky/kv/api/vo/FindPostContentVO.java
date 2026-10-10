package com.lucky.kv.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindPostContentVO {

    /**
     * 帖子 ID
     */
    private UUID uuid;

    /**
     * 帖子内容
     */
    private String content;
}
