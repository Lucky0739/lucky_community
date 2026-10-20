package com.lucky.common.mq;

import lombok.Data;

/**
 * 新帖发布后通知关注流。粉丝少的贴吧会推进关注者的收件箱，粉丝多的贴吧只写入吧的发件箱，等用户自己拉。
 */
@Data
public class BarFeedMessage {

    private Long postId;
    private Long barId;
    /** 发帖时间，毫秒 */
    private Long createTime;
}
