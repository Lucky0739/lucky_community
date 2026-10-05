package com.lucky.interaction.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.interaction.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return Result.success();
    }
}
