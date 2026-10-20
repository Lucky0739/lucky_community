package com.lucky.interaction.service;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.vo.UserPostSummaryVO;

public interface UserInteractionPostQueryService {

    PageResult<UserPostSummaryVO> pageLikedPosts(Long viewerId, UserPostListQueryDTO query);

    PageResult<UserPostSummaryVO> pageFavoritedPosts(Long viewerId, UserPostListQueryDTO query);
}
