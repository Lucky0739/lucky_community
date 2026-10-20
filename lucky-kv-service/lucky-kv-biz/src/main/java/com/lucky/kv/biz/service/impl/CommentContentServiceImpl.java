package com.lucky.kv.biz.service.impl;

import com.lucky.common.Response;
import com.lucky.kv.api.dto.BatchAddCommentContentDTO;
import com.lucky.kv.api.dto.CommentContentDTO;
import com.lucky.kv.api.dto.DeleteCommentContentDTO;
import com.lucky.kv.api.dto.FindCommentContentDTO;
import com.lucky.kv.api.vo.CommentContentItemVO;
import com.lucky.kv.biz.entity.CommentContent;
import com.lucky.kv.biz.entity.CommentContentPrimaryKey;
import com.lucky.kv.biz.repository.CommentContentRepository;
import com.lucky.kv.biz.service.CommentContentService;
import jakarta.annotation.Resource;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommentContentServiceImpl implements CommentContentService {

    @Resource
    private CassandraTemplate cassandraTemplate;

    @Resource
    private CommentContentRepository commentContentRepository;

    /**
     * 批量添加评论内容
     */
    @Override
    public Response<?> batchAddCommentContent(BatchAddCommentContentDTO batchAddCommentContentReqDTO) {
        List<CommentContentDTO> comments = batchAddCommentContentReqDTO.getComments();

        List<CommentContent> contentDOS = comments.stream()
                .map(commentContentDTO -> {
                    CommentContentPrimaryKey commentContentPrimaryKey = CommentContentPrimaryKey.builder()
                            .noteId(commentContentDTO.getPostId())
                            .yearMonth(commentContentDTO.getYearMonth())
                            .contentId(UUID.fromString(commentContentDTO.getContentId()))
                            .build();

                    return CommentContent.builder()
                            .primaryKey(commentContentPrimaryKey)
                            .content(commentContentDTO.getContent())
                            .build();
                }).toList();

        cassandraTemplate.batchOps()
                .insert(contentDOS)
                .execute();

        return Response.success();
    }

    @Override
    public List<CommentContentItemVO> findContents(List<FindCommentContentDTO> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        // Cassandra 查询要求分区键 noteId 用等值定死，所以先按帖子分组，每个帖子只发一次批量查询
        Map<Long, List<FindCommentContentDTO>> byPost = comments.stream()
                .collect(Collectors.groupingBy(FindCommentContentDTO::getPostId,
                        LinkedHashMap::new, Collectors.toList()));

        List<CommentContentItemVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<FindCommentContentDTO>> entry : byPost.entrySet()) {
            List<String> yearMonths = entry.getValue().stream()
                    .map(FindCommentContentDTO::getYearMonth)
                    .distinct()
                    .toList();
            List<UUID> contentIds = entry.getValue().stream()
                    .map(comment -> UUID.fromString(comment.getContentId()))
                    .distinct()
                    .toList();

            List<CommentContent> rows = commentContentRepository
                    .findContents(entry.getKey(), yearMonths, contentIds);
            for (CommentContent row : rows) {
                if (row.getContent() != null) {
                    result.add(new CommentContentItemVO(
                            row.getPrimaryKey().getContentId().toString(), row.getContent()));
                }
            }
        }
        return result;
    }

    @Override
    public Response<?> deleteCommentContent(DeleteCommentContentDTO deleteCommentContentDTO) {
        CommentContentPrimaryKey key = CommentContentPrimaryKey.builder()
                .noteId(deleteCommentContentDTO.getPostId())
                .yearMonth(deleteCommentContentDTO.getYearMonth())
                .contentId(UUID.fromString(deleteCommentContentDTO.getContentId()))
                .build();
        commentContentRepository.deleteById(key);
        return Response.success();
    }
}
