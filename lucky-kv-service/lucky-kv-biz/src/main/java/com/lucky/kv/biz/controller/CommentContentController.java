package com.lucky.kv.biz.controller;

import com.lucky.common.Response;
import com.lucky.common.Result;
import com.lucky.kv.api.dto.BatchAddCommentContentDTO;
import com.lucky.kv.api.dto.DeleteCommentContentDTO;
import com.lucky.kv.api.dto.FindCommentContentsDTO;
import com.lucky.kv.api.vo.CommentContentItemVO;
import com.lucky.kv.biz.service.CommentContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/kv")
@RequiredArgsConstructor
public class CommentContentController {

    private final CommentContentService commentContentService;

    /**
     * 批量存储评论内容
     */
    @PostMapping("/comment/content/batchAdd")
    public Response<?> batchAddCommentContent(@Validated @RequestBody BatchAddCommentContentDTO batchAddCommentContentReqDTO) {
        return commentContentService.batchAddCommentContent(batchAddCommentContentReqDTO);
    }

    /**
     * 按主键批量读取评论正文
     */
    @PostMapping("/comment/content/find")
    public Result<List<CommentContentItemVO>> findCommentContents(
            @Validated @RequestBody FindCommentContentsDTO findCommentContentsDTO) {
        return Result.success(commentContentService.findContents(findCommentContentsDTO.getComments()));
    }

    /**
     * 删除评论正文
     */
    @PostMapping("/comment/content/delete")
    public Response<?> deleteCommentContent(@Validated @RequestBody DeleteCommentContentDTO deleteCommentContentDTO) {
        return commentContentService.deleteCommentContent(deleteCommentContentDTO);
    }
}
