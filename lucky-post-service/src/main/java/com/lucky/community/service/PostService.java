package com.lucky.community.service;

import com.lucky.community.dto.CreatePostDTO;
import com.lucky.community.dto.PostFeedQueryDTO;
import com.lucky.community.dto.UpdatePostDTO;
import com.lucky.community.vo.PageVO;
import com.lucky.community.vo.PostBaseVO;
import com.lucky.community.vo.PostDetailVO;
import com.lucky.community.vo.PostSummaryVO;

import java.util.List;
import java.util.Map;

public interface PostService {

    /** 发帖。返回帖子本体：此刻 AI 还没跑，没有结论可填 */
    PostBaseVO createPost(Long userId, CreatePostDTO dto);

    /** 改帖。返回帖子本体：正文一变审核就被重置，旧结论不该再返回 */
    PostBaseVO updatePost(Long operatorId, Long postId, UpdatePostDTO dto);

    void deletePost(Long operatorId, Long postId);

    PostDetailVO getDetail(Long postId);

    PageVO<PostSummaryVO> feedPosts(PostFeedQueryDTO query);

    int warmUpRecentPosts(int limit);

    /** 每个吧最新一条可见帖，供吧列表展示。 */
    Map<Long, PostSummaryVO> latestVisibleByBarIds(List<Long> barIds);
}
