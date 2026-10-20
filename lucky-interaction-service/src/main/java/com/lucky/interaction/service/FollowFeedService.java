package com.lucky.interaction.service;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.vo.FollowFeedItemVO;

public interface FollowFeedService {

    /**
     * 新帖进入贴吧发件箱。粉丝数低于阈值时，再推进每个关注者的关注流。
     */
    void onPostPublished(Long barId, Long postId, long createTimeMillis);

    /**
     * 用户把关注的大吧发件箱拉进自己的 Redis 关注流。
     *
     * @return 这次从大吧拷进关注流的帖子条数
     */
    int pull(Long userId);

    PageResult<FollowFeedItemVO> page(Long userId, int pageNo, int pageSize);
}
