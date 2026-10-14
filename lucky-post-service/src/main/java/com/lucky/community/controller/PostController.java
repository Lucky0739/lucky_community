package com.lucky.community.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.file.FileService;
import com.lucky.common.vo.PageResult;
import com.lucky.community.dto.CreatePostDTO;
import com.lucky.community.dto.PostFeedQueryDTO;
import com.lucky.community.dto.UpdatePostDTO;
import com.lucky.community.service.PostService;
import com.lucky.community.vo.PageVO;
import com.lucky.community.vo.PostBaseVO;
import com.lucky.community.vo.PostDetailVO;
import com.lucky.community.vo.PostSummaryVO;
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

/**
 * 鉴权<b>逐个方法标注</b>，类上不再统一挂 {@code @AuthRequired}：
 */
@Slf4j
@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final FileService fileService;

    /**
     * 创建帖子
     * @param createPostDTO
     * @return 帖子本体，不含 AI 审核结论
     */
    @AuthRequired
    @PostMapping("/create")
    public Result<PostBaseVO> create(@RequestBody @Valid CreatePostDTO createPostDTO) {
        return Result.success(postService.createPost(UserContext.getUserId(), createPostDTO));
    }

    /**
     * 更新/编辑帖子
     * @param postId
     * @param dto
     * @return 帖子本体，不含 AI 审核结论
     */
    @AuthRequired
    @PutMapping("/{postId}")
    public Result<PostBaseVO> update(@PathVariable("postId") Long postId,
                                     @RequestBody @Valid UpdatePostDTO dto) {
        return Result.success(postService.updatePost(UserContext.getUserId(), postId, dto));
    }

    /**
     * 删除帖子
     * @param postId
     * @return
     */
    @AuthRequired
    @DeleteMapping("/{postId}")
    public Result<String> delete(@PathVariable("postId") Long postId) {
        postService.deletePost(UserContext.getUserId(), postId);
        return Result.success();
    }

    /**
     * 上传帖子图片
     * @param file
     * @return
     */
    @AuthRequired
    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_POST));
    }

    /**
     * 查看帖子详情
     * @param postId
     * @return
     */
    @AuthRequired
    @GetMapping("/{postId}")
    public Result<PostDetailVO> detail(@PathVariable("postId") Long postId) {
        return Result.success(postService.getDetail(postId));
    }

    /** 帖子列表 */
    @GetMapping("/feed")
    public Result<PageVO<PostSummaryVO>> feed(@ModelAttribute @Valid PostFeedQueryDTO query) {
        return Result.success(postService.feedPosts(query));
    }
}
