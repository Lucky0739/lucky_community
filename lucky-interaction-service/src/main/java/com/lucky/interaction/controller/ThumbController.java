package com.lucky.interaction.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.service.ThumbService;
import com.lucky.interaction.vo.UserPostSummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/thumb")
@AuthRequired
@RequiredArgsConstructor
public class ThumbController {

    private final ThumbService thumbService;

    /**
     * 点赞帖子。已点赞会返回业务错误，不会重复加计数。
     */
    @PostMapping("/{postId}")
    public Result<String> like(@PathVariable("postId") Long postId) {
        thumbService.like(UserContext.getUserId(), postId);
        return Result.success("点赞成功");
    }

    /**
     * 取消点赞。未点赞会返回业务错误，不会把计数减成负数。
     */
    @DeleteMapping("/{postId}")
    public Result<String> unlike(@PathVariable("postId") Long postId) {
        thumbService.unlike(UserContext.getUserId(), postId);
        return Result.success("取消点赞成功");
    }

    /**
     * 分页查询用户点赞过的帖子（默认当前登录用户，仅公开可见帖）
     */
    @GetMapping("/page")
    public Result<List<UserPostSummaryVO>> page(@ModelAttribute @Valid UserPostListQueryDTO query) {
        PageResult<UserPostSummaryVO> page = thumbService.pageLikedPosts(UserContext.getUserId(), query);
        return Result.success(page.getRecords(), page.getTotal());
    }
}
