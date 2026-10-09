package com.lucky.community.service;

import com.lucky.common.vo.PageResult;
import com.lucky.community.dto.AdminBarQueryDTO;
import com.lucky.community.dto.AdminBarStatusDTO;
import com.lucky.community.vo.BarVO;

/**
 * 平台管理员管贴吧。
 */
public interface AdminBarService {

    /** 分页查吧。status 不传则包含停用吧 */
    PageResult<BarVO> pageBars(Long operatorId, AdminBarQueryDTO query);

    /** 停用或恢复贴吧 */
    void updateStatus(Long operatorId, AdminBarStatusDTO dto);

    /** 删除贴吧：把状态改为停用，并清掉详情和列表缓存 */
    void deleteBar(Long operatorId, Long barId);
}
