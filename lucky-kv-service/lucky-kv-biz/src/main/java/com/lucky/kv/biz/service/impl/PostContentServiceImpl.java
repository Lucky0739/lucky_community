package com.lucky.kv.biz.service.impl;

import com.lucky.common.Response;
import com.lucky.common.exception.BusinessException;
import com.lucky.kv.api.dto.CreatePostContentRequest;
import com.lucky.kv.api.dto.DeletePostContentDTO;
import com.lucky.kv.api.dto.FindPostContentDTO;
import com.lucky.kv.api.vo.FindPostContentVO;
import com.lucky.kv.api.vo.PostContentVO;
import com.lucky.kv.biz.entity.PostContent;
import com.lucky.kv.biz.repository.PostContentRepository;
import com.lucky.kv.biz.service.PostContentService;
import com.lucky.kv.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostContentServiceImpl implements PostContentService {

    @Resource
    private PostContentRepository postContentRepository;

    @Override
    public PostContentVO create(CreatePostContentRequest createPostContentRequest) {
        String content = createPostContentRequest.getContent();
        // 与 tb_post.id 对齐：高位为 0，低位是雪花帖子 ID
        PostContent postContent = PostContent.builder()
                .id(new UUID(0L, createPostContentRequest.getPostId()))
                .content(content)
                .build();

        postContentRepository.save(postContent);
        return new PostContentVO(postContent.getId(),postContent.getContent());
    }

    /**
     * Cassandra查询帖子内容
     * @param findPostContentReqDTO
     * @return
     */
    @Override
    public FindPostContentVO findNoteContent(FindPostContentDTO findPostContentReqDTO) {
        String postId = findPostContentReqDTO.getPostId();
        Optional<PostContent> optional = postContentRepository.findById(UUID.fromString(postId));
        // 若笔记内容不存在
        if (optional.isEmpty()) {
            throw new BusinessException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND.getErrorMessage());
        }
        PostContent postContent = optional.get();
        return FindPostContentVO.builder()
                .uuid(postContent.getId())
                .content(postContent.getContent())
                .build();
    }

    @Override
    public Response<?> deletePostContent(DeletePostContentDTO deletePostContentReqDTO) {
        // 笔记 ID
        String noteId = deletePostContentReqDTO.getUuid();
        // 删除笔记内容
        postContentRepository.deleteById(UUID.fromString(noteId));
        return Response.success();
    }

}
