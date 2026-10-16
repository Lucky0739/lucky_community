package com.lucky.search.controller;

import com.lucky.search.dto.SearchPostDTO;
import com.lucky.search.service.SearchPostService;
import com.lucky.search.vo.PageResponse;
import com.lucky.search.vo.SearchPostRspVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索帖子。
 */
@RestController
@RequestMapping("/search")
public class PostController {

    @Resource
    private SearchPostService searchPostService;

    /**
     * 搜索帖子
     */
    @PostMapping("/post")
    public PageResponse<SearchPostRspVO> searchPost(@RequestBody @Validated SearchPostDTO searchPostDTO) {
        return searchPostService.searchPost(searchPostDTO);
    }
}
