package com.lucky.community.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lucky.community.vo.PostDetailVO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子缓存的存储单元：数据 + 它自己的逻辑过期时间
 */
@Data
public class PostDetailCacheEntry {

    /** 帖子详情 */
    private PostDetailVO data;

    /** 逻辑过期时间；超过它就算「旧」，读到旧值会返回旧值并触发异步重建 */
    private LocalDateTime logicalExpireAt;

    /**
     * 是否已逻辑过期。null 视为已过期，宁可重建也不要漏判
     * <p>{@code @JsonIgnore}：不标的话 Jackson 会把它当成一个属性一起序列化进缓存，
     * 存进去的是写入那一刻的快照，读出来时已经没意义了，纯噪音
     */
    @JsonIgnore
    public boolean isLogicallyExpired() {
        return logicalExpireAt == null || logicalExpireAt.isBefore(LocalDateTime.now());
    }
}
