package com.lucky.interaction.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.service.FavoriteService;
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
@RequestMapping("/favorite")
@AuthRequired
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{postId}")
    public Result<String> favorite(@PathVariable("postId") Long postId) {
        favoriteService.favorite(UserContext.getUserId(), postId);
        return Result.success("收藏成功");
    }

    @DeleteMapping("/{postId}")
    public Result<String> unfavorite(@PathVariable("postId") Long postId) {
        favoriteService.unfavorite(UserContext.getUserId(), postId);
        return Result.success("取消收藏成功");
    }

    @GetMapping("/page")
    public Result<List<UserPostSummaryVO>> page(@ModelAttribute @Valid UserPostListQueryDTO query) {
        PageResult<UserPostSummaryVO> page = favoriteService.pageFavoritedPosts(UserContext.getUserId(), query);
        return Result.success(page.getRecords(), page.getTotal());
    }
}
