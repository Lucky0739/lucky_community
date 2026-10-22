package com.lucky.community.dto;

import com.lucky.common.dto.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子列表的游标分页查询（走查询参数，非请求体）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostFeedQueryDTO extends PageQueryDTO {

    /** 贴吧ID。为空表示不限贴吧，走全站流；传了则只看该吧 */
    private Long barId;

    /** 作者user_id，为空表示不限作者 */
    private Long userId;

    /**
     * 贴吧流的热度游标，和 {@code lastId} 一起用。
     * 热度 = 点赞数 × 1 + 收藏数 × 2。全站流不传。
     */
    private Long lastScore;
}
