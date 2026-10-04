package com.lucky.community.bar.service;

import com.lucky.community.bar.dto.BarQueryDTO;
import com.lucky.community.bar.dto.CreateBarDTO;
import com.lucky.community.bar.dto.UpdateBarDTO;
import com.lucky.community.bar.vo.BarVO;
import com.lucky.community.common.PageResult;

public interface BarService {

    BarVO createBar(Long userId, CreateBarDTO createBarDTO);

    BarVO updateBar(Long operatorId, Long barId, UpdateBarDTO dto);

    void deleteBar(Long operatorId, Long barId);

    PageResult<BarVO> pageBars(BarQueryDTO query);

    BarVO getBar(Long barId);
}
