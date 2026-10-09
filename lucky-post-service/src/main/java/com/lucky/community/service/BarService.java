package com.lucky.community.service;

import com.lucky.community.dto.BarQueryDTO;
import com.lucky.community.dto.CreateBarDTO;
import com.lucky.community.dto.UpdateBarDTO;
import com.lucky.community.vo.BarVO;
import com.lucky.common.vo.PageResult;

public interface BarService {

    BarVO createBar(Long userId, CreateBarDTO createBarDTO);

    BarVO updateBar(Long operatorId, Long barId, UpdateBarDTO dto);

    void deleteBar(Long operatorId, Long barId);

    PageResult<BarVO> pageBars(BarQueryDTO query);

    BarVO getBarDetail(Long barId);
}
