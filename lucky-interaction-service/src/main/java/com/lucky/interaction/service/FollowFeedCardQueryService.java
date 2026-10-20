package com.lucky.interaction.service;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.vo.FollowFeedCardVO;

public interface FollowFeedCardQueryService {

    PageResult<FollowFeedCardVO> pageCards(Long viewerId, int pageNo, int pageSize);
}
