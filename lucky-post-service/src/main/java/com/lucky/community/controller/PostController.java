package com.lucky.community.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.file.FileService;
import com.lucky.community.common.PageResult;
import com.lucky.community.dto.CreatePostDTO;
import com.lucky.community.dto.PostFeedQueryDTO;
import com.lucky.community.dto.PostQueryDTO;
import com.lucky.community.dto.UpdatePostDTO;
import com.lucky.community.service.PostService;
import com.lucky.community.vo.PageVO;
import com.lucky.community.vo.PostDetailVO;
import com.lucky.community.vo.PostSummaryVO;
import com.lucky.community.vo.PostViewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Slf4j
@RestController
@RequestMapping("/post")
@AuthRequired
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final FileService fileService;

    @PostMapping
    public Result<PostDetailVO> create(@RequestBody @Valid CreatePostDTO createPostDTO) {
        return Result.success(postService.createPost(UserContext.getUserId(), createPostDTO));
    }

    @PutMapping("/{postId}")
    public Result<PostDetailVO> update(@PathVariable("postId") Long postId,
                                       @RequestBody @Valid UpdatePostDTO dto) {
        return Result.success(postService.updatePost(UserContext.getUserId(), postId, dto));
    }

    @DeleteMapping("/{postId}")
    public Result<String> delete(@PathVariable("postId") Long postId) {
        postService.deletePost(UserContext.getUserId(), postId);
        return Result.success();
    }

    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_POST));
    }

    @GetMapping("/{postId}")
    public Result<PostDetailVO> detail(@PathVariable("postId") Long postId) {
        return Result.success(postService.getDetail(postId));
    }

    @GetMapping("/{postId}/view")
    public Result<PostViewVO> view(@PathVariable("postId") Long postId) {
        return Result.success(postService.getView(postId));
    }

    @GetMapping("/page")
    public Result<java.util.List<PostSummaryVO>> page(@ModelAttribute @Valid PostQueryDTO query) {
        PageResult<PostSummaryVO> page = postService.pagePosts(query);
        return Result.success(page.getRecords(), page.getTotal());
    }

    @GetMapping("/feed")
    public Result<PageVO<PostSummaryVO>> feed(@ModelAttribute @Valid PostFeedQueryDTO query) {
        return Result.success(postService.feedPosts(query));
    }
}
