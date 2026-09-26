package com.lucky.core.bar.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lucky.core.bar.dto.BarQueryDTO;
import com.lucky.core.bar.dto.CreateBarDTO;
import com.lucky.core.bar.dto.UpdateBarDTO;
import com.lucky.core.bar.vo.BarVO;

/**
 * 贴吧
 */
public interface BarService {

    /**
     * 建吧
     */
    BarVO createBar(Long userId, CreateBarDTO dto);

    /**
     * 修改贴吧
     */
    BarVO updateBar(Long operatorId, Long barId, UpdateBarDTO dto);

    /**
     * 删吧：把状态置为停用。
     */
    void deleteBar(Long operatorId, Long barId);

    /**
     * 分页查吧
     */
    IPage<BarVO> pageBars(BarQueryDTO query);

    /**
     * 查吧详情
     */
    BarVO getBar(Long barId);
}
