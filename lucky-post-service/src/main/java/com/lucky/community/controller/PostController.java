package com.lucky.community.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.file.FileService;
import com.lucky.common.vo.PageResult;
import com.lucky.community.ai.PostImageDraftService;
import com.lucky.community.dto.CreatePostDTO;
import com.lucky.community.dto.PostFeedQueryDTO;
import com.lucky.community.dto.PostImageDraftDTO;
import com.lucky.community.dto.UpdatePostDTO;
import com.lucky.community.service.PostService;
import com.lucky.community.vo.PageVO;
import com.lucky.community.vo.PostBaseVO;
import com.lucky.community.vo.PostDetailVO;
import com.lucky.community.vo.PostImageDraftVO;
import com.lucky.community.vo.PostModerationStatusVO;
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

import java.util.List;

/**
 * 帖子模块
 */
@Slf4j
@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final FileService fileService;
    private final PostImageDraftService postImageDraftService;

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
     * 根据已上传图片生成标题和正文。只建议内容，不创建帖子。
     */
    @AuthRequired
    @PostMapping("/draft")
    public Result<PostImageDraftVO> draft(@RequestBody @Valid PostImageDraftDTO dto) {
        return Result.success(postImageDraftService.draft(dto.getImageUrls()));
    }

    /** 帖子列表 */
    @GetMapping("/feed")
    public Result<PageVO<PostSummaryVO>> feed(@ModelAttribute @Valid PostFeedQueryDTO query) {
        return Result.success(postService.feedPosts(query));
    }


    /** 全站最近可见帖，首页发现流 */
    @GetMapping("/recent")
    public Result<List<PostSummaryVO>> recent(@RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(postService.listRecentVisible(pageSize));
    }


    /**
     * 查看帖子详情
     * @param postId
     * @return
     */
    @AuthRequired
    @GetMapping("/{postId}")
    public Result<PostDetailVO> detail(@PathVariable("postId") Long postId) {
        return Result.success(postService.getDetail(postId, UserContext.getUserId()));
    }

    /**
     * 作者轮询自己帖子的 AI 审核进度。
     * 非作者或帖子不存在时 data=null，前端据此停止轮询，不泄露帖子是否存在。
     */
    @AuthRequired
    @GetMapping("/{postId}/moderation-status")
    public Result<PostModerationStatusVO> moderationStatus(@PathVariable("postId") Long postId) {
        return Result.success(postService.getModerationStatus(postId, UserContext.getUserId()));
    }
}
