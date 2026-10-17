package com.lucky.community.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.vo.PageResult;
import com.lucky.community.dto.AdminPostQueryDTO;
import com.lucky.community.dto.AdminReviewPostDTO;
import com.lucky.community.service.AdminPostService;
import com.lucky.community.vo.AdminPostVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台管理员管帖子：分页查看、人工审核、直接删帖。
 */
@RestController
@RequestMapping("/admin/post")
@AuthRequired
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    /**
     * 分页查询帖子
     * @param query
     * @return
     */
    @GetMapping("/page")
    public Result<List<AdminPostVO>> page(@ModelAttribute @Valid AdminPostQueryDTO query) {
        PageResult<AdminPostVO> page = adminPostService.pagePosts(UserContext.getUserId(), query);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 人工审核帖子
     * @param adminReviewPostDTO
     * @return
     */
    @PostMapping("/review")
    public Result<String> review(@RequestBody @Valid AdminReviewPostDTO adminReviewPostDTO) {
        adminPostService.reviewPost(UserContext.getUserId(), adminReviewPostDTO);
        return Result.success();
    }

    /**
     * 手动重跑某帖的 AI 审核。用于自动重试已用尽的失败帖
     * @param postId
     * @return
     */
    @PostMapping("/{postId}/reanalyze")
    public Result<String> reanalyze(@PathVariable("postId") Long postId) {
        adminPostService.reanalyze(UserContext.getUserId(), postId);
        return Result.success();
    }

    /**
     * 触发搜索索引全量重建（异步跑，立刻返回）。
     * 用于把历史帖子补进 ES，或增量同步漏掉时事后对账。
     * @return
     */
    @PostMapping("/reindex")
    public Result<String> reindex() {
        adminPostService.reindex(UserContext.getUserId());
        return Result.success();
    }

    /**
     * 删除帖子
     * @param postId
     * @return
     */
    @DeleteMapping("/{postId}")
    public Result<String> delete(@PathVariable("postId") Long postId) {
        adminPostService.deletePost(UserContext.getUserId(), postId);
        return Result.success();
    }
}
