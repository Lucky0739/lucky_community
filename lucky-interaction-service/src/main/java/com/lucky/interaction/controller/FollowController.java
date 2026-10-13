package com.lucky.interaction.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.FollowListQueryDTO;
import com.lucky.interaction.service.FollowService;
import com.lucky.interaction.vo.FollowUserVO;
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
@RequestMapping("/follow")
@AuthRequired
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /**
     * 关注指定用户
     */
    @PostMapping("/{followUserId}")
    public Result<String> follow(@PathVariable("followUserId") Long followUserId) {
        followService.follow(UserContext.getUserId(), followUserId);
        return Result.success("关注成功");
    }

    /**
     * 关注列表。可按昵称关键词分页。每条带当前登录用户对该用户的关注状态，列表里可直接取消关注
     */
    @GetMapping("/page")
    public Result<List<FollowUserVO>> page(@ModelAttribute @Valid FollowListQueryDTO followListQueryDTO) {
        PageResult<FollowUserVO> page = followService.pageFollowees(UserContext.getUserId(), followListQueryDTO);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 是否已关注指定用户
     */
    @GetMapping("/{followUserId}")
    public Result<Boolean> isFollowed(@PathVariable("followUserId") Long followUserId) {
        return Result.success(followService.isFollowed(UserContext.getUserId(), followUserId));
    }

    /**
     * 取消关注指定用户
     */
    @DeleteMapping("/{followUserId}")
    public Result<String> unfollow(@PathVariable("followUserId") Long followUserId) {
        followService.unfollow(UserContext.getUserId(), followUserId);
        return Result.success("取消关注成功");
    }
}
