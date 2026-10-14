package com.lucky.community.service;

import com.lucky.common.vo.PageResult;
import com.lucky.community.dto.AdminPostQueryDTO;
import com.lucky.community.dto.AdminReviewPostDTO;
import com.lucky.community.vo.AdminPostVO;

/**
 * 平台管理员管帖子。
 */
public interface AdminPostService {

    /** 分页查帖。pending 为 true 时只看还没人工处理的 */
    PageResult<AdminPostVO> pagePosts(Long operatorId, AdminPostQueryDTO query);

    /** 人工审核。未处置、警告留在列表里；删帖、封禁会把帖子逻辑删除 */
    void reviewPost(Long operatorId, AdminReviewPostDTO dto);

    /** 平台管理员直接删帖 */
    void deletePost(Long operatorId, Long postId);

    /**
     * 手动重跑某帖的 AI 审核。用于自动重试已用尽、落进「人工待审」的失败帖：
     * 清零重试计数后重投 {@code /govern}，让自动重试也能重新接管。
     */
    void reanalyze(Long operatorId, Long postId);
}
