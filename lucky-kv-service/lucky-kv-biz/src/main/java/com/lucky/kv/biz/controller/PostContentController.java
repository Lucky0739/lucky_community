package com.lucky.kv.biz.controller;

import com.lucky.common.Response;
import com.lucky.common.Result;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.kv.api.dto.CreatePostContentRequest;
import com.lucky.kv.api.dto.DeletePostContentDTO;
import com.lucky.kv.api.dto.FindPostContentDTO;
import com.lucky.kv.api.vo.FindPostContentVO;
import com.lucky.kv.api.vo.PostContentVO;
import com.lucky.kv.biz.service.PostContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/kv")
@RequiredArgsConstructor
public class PostContentController {

    private final PostContentService postContentService;

    /**
     * 创建帖子内容
     * @param createPostContentRequest
     * @return
     */
    @AuthRequired
    @PostMapping("/post/content/create")
    public Result<PostContentVO> create(@RequestBody @Valid CreatePostContentRequest createPostContentRequest) {
        return Result.success(postContentService.create(createPostContentRequest));
    }

    /**
     * 根据帖子内容id获取帖子内容
     *
     * @param findPostContentDTO
     * @return
     */
    @PostMapping(value = "/post/content/find")
    public Result<FindPostContentVO> get(@Validated @RequestBody FindPostContentDTO findPostContentDTO) {
        return Result.success(postContentService.findNoteContent(findPostContentDTO));
    }

    /**
     * 删除帖子
     * @param deleteNoteContentReqDTO
     * @return
     */
    @PostMapping(value = "/post/content/delete")
    public Response<?> deleteNoteContent(@Validated @RequestBody DeletePostContentDTO deleteNoteContentReqDTO) {
        return postContentService.deletePostContent(deleteNoteContentReqDTO);
    }
}
