package com.lucky.community.service;

import com.lucky.community.common.PageResult;
import com.lucky.community.dto.CreatePostDTO;
import com.lucky.community.dto.PostFeedQueryDTO;
import com.lucky.community.dto.PostQueryDTO;
import com.lucky.community.dto.UpdatePostDTO;
import com.lucky.community.vo.PageVO;
import com.lucky.community.vo.PostDetailVO;
import com.lucky.community.vo.PostSummaryVO;
import com.lucky.community.vo.PostViewVO;

public interface PostService {

    PostDetailVO createPost(Long userId, CreatePostDTO dto);

    PostDetailVO updatePost(Long operatorId, Long postId, UpdatePostDTO dto);

    void deletePost(Long operatorId, Long postId);

    PostDetailVO getDetail(Long postId);

    PostViewVO getView(Long postId);

    PageResult<PostSummaryVO> pagePosts(PostQueryDTO query);

    PageVO<PostSummaryVO> feedPosts(PostFeedQueryDTO query);

    int warmUpRecentPosts(int limit);
}
