package com.lucky.search.service;

import com.lucky.search.dto.SearchPostDTO;
import com.lucky.search.vo.PageResponse;
import com.lucky.search.vo.SearchPostRspVO;

public interface SearchPostService {

    /**
     * 搜索帖子
     *
     * @param searchPostDTO
     * @return
     */
    PageResponse<SearchPostRspVO> searchPost(SearchPostDTO searchPostDTO);
}
