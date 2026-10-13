package com.lucky.comment.controller;

import com.lucky.comment.dto.PublishCommentDTO;
import com.lucky.comment.service.CommentPublishService;
import com.lucky.common.Result;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.file.FileService;
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
@AuthRequired
@RequiredArgsConstructor
public class CommentController {

    private final CommentPublishService commentPublishService;
    private final FileService fileService;

    /**
     * 发表评论。马上返回评论 ID，正文由消息消费后写入数据库。
     */
    @PostMapping("/create")
    public Result<Long> publish(@RequestBody @Valid PublishCommentDTO publishCommentDTO) {
        return Result.success(commentPublishService.publish(publishCommentDTO));
    }

    /**
     * 上传评论配图。返回可访问 URL，发评论时作为 imageUrl 传入。
     */
    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_COMMENT));
    }
}
