package com.lucky.comment.controller;

import com.lucky.comment.dto.CommentLikeDTO;
import com.lucky.comment.dto.DeleteCommentDTO;
import com.lucky.comment.dto.FindCommentPageListDTO;
import com.lucky.comment.dto.FindCommentReplyPageListDTO;
import com.lucky.comment.dto.FindUserCommentPageDTO;
import com.lucky.comment.dto.PublishCommentDTO;
import com.lucky.comment.service.CommentService;
import com.lucky.common.auth.UserContext;
import com.lucky.comment.vo.FindCommentPageListVO;
import com.lucky.comment.vo.UserCommentItemVO;
import com.lucky.common.Result;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.file.FileService;
import com.lucky.common.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final FileService fileService;

    /**
     * 评论列表分页。
     */
    @PostMapping("/page")
    public Result<PageResult<FindCommentPageListVO>> page(@RequestBody @Valid FindCommentPageListDTO findCommentPageListDTO) {
        return Result.success(commentService.findCommentPageList(
                findCommentPageListDTO, UserContext.getUserId()));
    }

    /**
     * 二级评论分页（某条一级评论下的全部回复）。
     */
    @PostMapping("/reply/page")
    public Result<PageResult<FindCommentPageListVO>> replyPage(
            @RequestBody @Valid FindCommentReplyPageListDTO findCommentReplyPageListDTO) {
        return Result.success(commentService.findReplyPageList(
                findCommentReplyPageListDTO, UserContext.getUserId()));
    }

    /**
     * 用户回贴/评论动态（个人主页「回贴」Tab）。
     */
    @AuthRequired
    @PostMapping("/user/page")
    public Result<PageResult<UserCommentItemVO>> userPage(@RequestBody @Valid FindUserCommentPageDTO dto) {
        return Result.success(commentService.findUserCommentPage(dto, UserContext.getUserId()));
    }

    /**
     * 发表评论。马上返回评论 ID，正文由消息消费后写入数据库。
     */
    @AuthRequired
    @PostMapping("/create")
    public Result<Long> publish(@RequestBody @Valid PublishCommentDTO publishCommentDTO) {
        return Result.success(commentService.publish(publishCommentDTO));
    }

    /**
     * 删除评论
     * @param deleteCommentDTO
     * @return
     */
    @AuthRequired
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody @Valid DeleteCommentDTO deleteCommentDTO) {
        commentService.delete(UserContext.getUserId(), deleteCommentDTO.getCommentId());
        return Result.success(null);
    }

    @AuthRequired
    @PostMapping("/like")
    public Result<Void> like(@RequestBody @Valid CommentLikeDTO commentLikeDTO) {
        commentService.like(UserContext.getUserId(), commentLikeDTO.getCommentId());
        return Result.success(null);
    }

    @AuthRequired
    @PostMapping("/unlike")
    public Result<Void> unlike(@RequestBody @Valid CommentLikeDTO commentLikeDTO) {
        commentService.unlike(UserContext.getUserId(), commentLikeDTO.getCommentId());
        return Result.success(null);
    }

    /**
     * 上传评论配图。返回可访问 URL，发评论时作为 imageUrl 传入。
     */
    @AuthRequired
    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_COMMENT));
    }
}
