package com.lucky.kv.api.api;

import com.lucky.common.Response;
import com.lucky.common.Result;
import com.lucky.kv.api.constant.ApiConstants;
import com.lucky.kv.api.dto.CreatePostContentRequest;
import com.lucky.kv.api.dto.DeletePostContentDTO;
import com.lucky.kv.api.dto.FindPostContentDTO;
import com.lucky.kv.api.vo.FindPostContentVO;
import com.lucky.kv.api.vo.PostContentVO;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface KeyValueFeignApi {

    /**
     * 服务内路径前缀
     */
    String PREFIX = "/kv";

    /**
     * 创建帖子内容
     */
    @PostMapping(PREFIX + "/post/content/create")
    Result<PostContentVO> create(@RequestBody @Valid CreatePostContentRequest createPostContentRequest);

    /**
     * 查询帖子内容
     */
    @PostMapping(PREFIX + "/post/content/find")
    Result<FindPostContentVO> find(@RequestBody @Valid FindPostContentDTO findPostContentDTO);

    /**
     * 删除帖子内容
     */
    @PostMapping(PREFIX + "/note/content/delete")
    Response<?> deleteNoteContent(@RequestBody @Valid DeletePostContentDTO deleteNoteContentReqDTO);
}
