package com.lucky.interaction.service;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.vo.UserPostSummaryVO;

public interface ThumbService {

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);

    PageResult<UserPostSummaryVO> pageLikedPosts(Long viewerId, UserPostListQueryDTO query);
}
