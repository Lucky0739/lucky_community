package com.lucky.core.post.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lucky.common.Result;
import com.lucky.core.auth.annotation.AuthRequired;
import com.lucky.ratelimit.RateLimit;
import com.lucky.core.post.dto.CreatePostDTO;
import com.lucky.core.post.dto.PostFeedQueryDTO;
import com.lucky.core.post.dto.PostQueryDTO;
import com.lucky.core.post.dto.UpdatePostDTO;
import com.lucky.core.file.FileService;
import com.lucky.core.post.service.PostService;
import com.lucky.core.auth.UserContext;
import com.lucky.core.post.vo.PageVO;
import com.lucky.core.post.vo.PostDetailVO;
import com.lucky.core.post.vo.PostSummaryVO;
import com.lucky.core.post.vo.PostViewVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 帖子模块
 */
@Slf4j
@RestController
@RequestMapping("/post")
@AuthRequired
public class PostController {

    @Resource
    private PostService postService;

    @Resource
    private FileService fileService;

    /**
     * 发帖
     */
    @PostMapping
    public Result<PostDetailVO> create(@RequestBody @Valid CreatePostDTO createPostDTO) {
        return Result.success(postService.createPost(UserContext.getUserId(), createPostDTO));
    }

    /**
     * 修改帖子
     */
    @PutMapping("/{postId}")
    public Result<PostDetailVO> update(@PathVariable("postId") Long postId,
                                       @RequestBody @Valid UpdatePostDTO dto) {
        return Result.success(postService.updatePost(UserContext.getUserId(), postId, dto));
    }

    /**
     * 删除帖子
     */
    @DeleteMapping("/{postId}")
    public Result<String> delete(@PathVariable("postId") Long postId) {
        postService.deletePost(UserContext.getUserId(), postId);
        return Result.success();
    }

    /**
     * 预上传帖子图片
     */
    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_POST));
    }
    /**
     * 帖子详情（不含评论）。刷新正文 / 不需要楼层时用这个。
     */
    @GetMapping("/{postId}")
    public Result<PostDetailVO> detail(@PathVariable("postId") Long postId) {
        return Result.success(postService.getDetail(postId));
    }

    /**
     * 详情首屏：帖子 + 一级评论首页。评论不进帖子缓存；继续翻页 / 展开回复走评论接口。
     */
    @GetMapping("/{postId}/view")
    @RateLimit(key = "post:view", capacity = 120, refillPerSecond = 2)
    public Result<PostViewVO> view(@PathVariable("postId") Long postId) {
        return Result.success(postService.getView(postId));
    }

    /**
     * 分页查帖子列表
     */
    @GetMapping("/page")
    @RateLimit(key = "post:page")
    public Result<List<PostSummaryVO>> page(@ModelAttribute @Valid PostQueryDTO query) {
        IPage<PostSummaryVO> page = postService.pagePosts(query);
        return Result.success(page.getRecords(), null);
    }
    /**
     * 帖子列表（游标分页，<b>推荐入口</b>）
     */
    @GetMapping("/feed")
    @RateLimit(key = "post:feed", capacity = 120, refillPerSecond = 2)
    public Result<PageVO<PostSummaryVO>> feed(@ModelAttribute @Valid PostFeedQueryDTO query) {
        return Result.success(postService.feedPosts(query));
    }
}
