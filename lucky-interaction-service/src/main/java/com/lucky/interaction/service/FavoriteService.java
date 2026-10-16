package com.lucky.interaction.service;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.vo.UserPostSummaryVO;

public interface FavoriteService {

    void favorite(Long userId, Long postId);

    void unfavorite(Long userId, Long postId);

    PageResult<UserPostSummaryVO> pageFavoritedPosts(Long viewerId, UserPostListQueryDTO query);
}
