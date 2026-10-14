package com.lucky.kv.biz.service;

import com.lucky.common.Response;
import com.lucky.kv.api.dto.BatchAddCommentContentDTO;
import com.lucky.kv.api.dto.FindCommentContentDTO;
import com.lucky.kv.api.vo.CommentContentItemVO;

import java.util.List;

public interface CommentContentService {

    /**
     * 批量添加评论内容
     */
    Response<?> batchAddCommentContent(BatchAddCommentContentDTO batchAddCommentContentReqDTO);

    /**
     * 按帖子、年月和正文 ID 读取评论正文。
     */
    List<CommentContentItemVO> findContents(List<FindCommentContentDTO> comments);
}
