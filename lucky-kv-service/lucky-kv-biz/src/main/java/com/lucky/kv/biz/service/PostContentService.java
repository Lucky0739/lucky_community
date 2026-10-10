package com.lucky.kv.biz.service;

import com.lucky.common.Response;
import com.lucky.kv.api.dto.CreatePostContentRequest;
import com.lucky.kv.api.dto.DeletePostContentDTO;
import com.lucky.kv.api.dto.FindPostContentDTO;
import com.lucky.kv.api.vo.FindPostContentVO;
import com.lucky.kv.api.vo.PostContentVO;

public interface PostContentService {

    PostContentVO create(CreatePostContentRequest request);

    /**
     * 查询帖子内容
     *
     * @param findPostContentReqDTO
     * @return
             */
    FindPostContentVO findNoteContent(FindPostContentDTO findPostContentReqDTO);

    /**
     * 删除笔记内容
     *
     * @param deletePostContentReqDTO
     * @return
     */
    Response<?> deletePostContent(DeletePostContentDTO deletePostContentReqDTO);

}
