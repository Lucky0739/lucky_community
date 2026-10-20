package com.lucky.kv.api.api;

import com.lucky.common.Response;
import com.lucky.common.Result;
import com.lucky.kv.api.constant.ApiConstants;
import com.lucky.kv.api.dto.BatchAddCommentContentDTO;
import com.lucky.kv.api.dto.CreatePostContentRequest;
import com.lucky.kv.api.dto.DeleteCommentContentDTO;
import com.lucky.kv.api.dto.DeletePostContentDTO;
import com.lucky.kv.api.dto.FindCommentContentsDTO;
import com.lucky.kv.api.dto.FindPostContentDTO;
import com.lucky.kv.api.vo.CommentContentItemVO;
import com.lucky.kv.api.vo.FindPostContentVO;
import com.lucky.kv.api.vo.PostContentVO;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME, contextId = "keyValueFeignApi")
public interface KeyValueFeignApi {

    @PostMapping("/kv/post/content/create")
    Result<PostContentVO> create(@RequestBody @Valid CreatePostContentRequest createPostContentRequest);

    @PostMapping("/kv/post/content/find")
    Result<FindPostContentVO> find(@RequestBody @Valid FindPostContentDTO findPostContentDTO);

    @PostMapping("/kv/note/content/delete")
    Response<Void> deleteNoteContent(@RequestBody @Valid DeletePostContentDTO deleteNoteContentReqDTO);

    @PostMapping("/kv/comment/content/batchAdd")
    Response<Void> batchAddCommentContent(@RequestBody @Valid BatchAddCommentContentDTO batchAddCommentContentDTO);

    @PostMapping("/kv/comment/content/find")
    Result<List<CommentContentItemVO>> findCommentContents(@RequestBody @Valid FindCommentContentsDTO findCommentContentsDTO);

    @PostMapping("/kv/comment/content/delete")
    Response<Void> deleteCommentContent(@RequestBody @Valid DeleteCommentContentDTO deleteCommentContentDTO);
}
