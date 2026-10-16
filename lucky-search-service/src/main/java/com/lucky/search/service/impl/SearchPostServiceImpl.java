package com.lucky.search.service.impl;

import com.lucky.search.domain.PostDocument;
import com.lucky.search.dto.SearchPostDTO;
import com.lucky.search.index.PostIndex;
import com.lucky.search.service.SearchPostService;
import com.lucky.search.vo.PageResponse;
import com.lucky.search.vo.SearchPostRspVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchPostServiceImpl implements SearchPostService {

    private static final int PAGE_SIZE = 10;

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 搜索帖子。标题权重 2，正文权重 1。命中结果按发布时间从新到旧排。
     */
    @Override
    public PageResponse<SearchPostRspVO> searchPost(SearchPostDTO searchPostDTO) {
        String keyword = searchPostDTO.getKeyword();
        int pageNo = searchPostDTO.getPageNo() == null ? 1 : searchPostDTO.getPageNo();
        int from = (pageNo - 1) * PAGE_SIZE;

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(bool -> {
                    bool.must(must -> must.multiMatch(match -> match
                            .query(keyword)
                            .fields(PostIndex.FIELD_TITLE + "^2", PostIndex.FIELD_CONTENT)));
                    bool.filter(filter -> filter.term(term -> term
                            .field(PostIndex.FIELD_DELETED)
                            .value(0)));
                    // 旧文档没写 visibility_scope，缺字段按公开处理
                    bool.filter(filter -> filter.bool(visible -> visible
                            .should(should -> should.term(term -> term
                                    .field(PostIndex.FIELD_VISIBILITY_SCOPE)
                                    .value(0)))
                            .should(should -> should.bool(missing -> missing
                                    .mustNot(mustNot -> mustNot.exists(exists -> exists
                                            .field(PostIndex.FIELD_VISIBILITY_SCOPE)))))
                            .minimumShouldMatch("1")));
                    return bool;
                }))
                .withSort(Sort.by(Sort.Order.desc(PostIndex.FIELD_CREATE_TIME)))
                .withHighlightQuery(new HighlightQuery(titleHighlight(), PostDocument.class))
                .withPageable(PageRequest.of(from / PAGE_SIZE, PAGE_SIZE))
                .build();

        List<SearchPostRspVO> records = new ArrayList<>();
        long total = 0;
        try {
            log.info("==> SearchRequest: index={}, keyword={}, from={}, size={}",
                    PostIndex.NAME, keyword, from, PAGE_SIZE);
            SearchHits<PostDocument> hits = elasticsearchOperations.search(query, PostDocument.class);
            total = hits.getTotalHits();
            log.info("==> 命中文档总数, hits: {}", total);
            for (SearchHit<PostDocument> hit : hits.getSearchHits()) {
                records.add(toRsp(hit));
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticsearch 异常: ", e);
        }
        return PageResponse.success(records, pageNo, total);
    }

    private Highlight titleHighlight() {
        return new Highlight(
                HighlightParameters.builder().withPreTags("<strong>").withPostTags("</strong>").build(),
                List.of(new HighlightField(PostIndex.FIELD_TITLE)));
    }

    private SearchPostRspVO toRsp(SearchHit<PostDocument> hit) {
        PostDocument document = hit.getContent();
        List<String> titleHighlights = hit.getHighlightFields().get(PostIndex.FIELD_TITLE);
        String highlightTitle = titleHighlights == null || titleHighlights.isEmpty() ? null : titleHighlights.get(0);
        return SearchPostRspVO.builder()
                .postId(document.getPostId())
                .barId(document.getBarId())
                .userId(document.getUserId())
                .coverImage(document.getCoverImage())
                .title(document.getTitle())
                .highlightTitle(highlightTitle)
                .likeCount(document.getLikeCount())
                .createTime(document.getCreateTime())
                .build();
    }
}
