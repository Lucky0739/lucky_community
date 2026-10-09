package com.lucky.community.service.impl;

import com.lucky.common.exception.BusinessException;
import com.lucky.common.vo.PageResult;
import com.lucky.community.cache.BarDetailCache;
import com.lucky.community.cache.BarPageCache;
import com.lucky.community.dto.AdminBarQueryDTO;
import com.lucky.community.dto.AdminBarStatusDTO;
import com.lucky.community.entity.Bar;
import com.lucky.community.mapper.BarMapper;
import com.lucky.community.service.AdminBarService;
import com.lucky.community.support.PermissionSupport;
import com.lucky.community.support.entity.UserBrief;
import com.lucky.community.support.mapper.UserReadMapper;
import com.lucky.community.vo.BarVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminBarServiceImpl implements AdminBarService {

    private final BarMapper barMapper;
    private final UserReadMapper userReadMapper;
    private final PermissionSupport permissionSupport;
    private final BarDetailCache barDetailCache;
    private final BarPageCache barPageCache;

    @Override
    public PageResult<BarVO> pageBars(Long operatorId, AdminBarQueryDTO query) {
        requirePlatformAdmin(operatorId);
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        long total = barMapper.countAdminPage(query.getStatus(), keyword);
        PageResult<BarVO> result = new PageResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(total);
        if ((long) (pageNum - 1) * pageSize >= total) {
            result.setRecords(List.of());
            return result;
        }
        List<Bar> bars = barMapper.selectAdminPage(query.getStatus(), keyword, pageSize, (pageNum - 1) * pageSize);
        result.setRecords(toVOs(bars));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long operatorId, AdminBarStatusDTO dto) {
        requirePlatformAdmin(operatorId);
        if (dto.getStatus() != Bar.STATUS_NORMAL && dto.getStatus() != Bar.STATUS_DISABLED) {
            throw new BusinessException("状态只能是 1 正常或 0 停用");
        }
        Bar bar = barMapper.selectById(dto.getBarId());
        if (bar == null) {
            throw new BusinessException("贴吧不存在");
        }
        barMapper.updateStatus(dto.getBarId(), dto.getStatus());
        runAfterCommit(() -> {
            barDetailCache.evict(dto.getBarId());
            barPageCache.invalidate();
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBar(Long operatorId, Long barId) {
        requirePlatformAdmin(operatorId);
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            throw new BusinessException("贴吧不存在");
        }
        if (bar.getStatus() != null && bar.getStatus() == Bar.STATUS_DISABLED) {
            throw new BusinessException("贴吧已停用");
        }
        barMapper.updateStatus(barId, Bar.STATUS_DISABLED);
        runAfterCommit(() -> {
            barDetailCache.evict(barId);
            barPageCache.invalidate();
        });
    }

    private List<BarVO> toVOs(List<Bar> bars) {
        Set<Long> ownerIds = bars.stream()
                .map(Bar::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserBrief> ownerById = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (UserBrief user : userReadMapper.selectByIds(ownerIds.stream().toList())) {
                ownerById.put(user.getId(), user);
            }
        }
        return bars.stream()
                .map(bar -> toBarVO(bar, ownerById.get(bar.getOwnerUserId())))
                .toList();
    }

    private BarVO toBarVO(Bar bar, UserBrief owner) {
        BarVO vo = new BarVO();
        vo.setBarId(bar.getId());
        vo.setName(bar.getName());
        vo.setDescription(bar.getDescription());
        vo.setAvatar(bar.getAvatar());
        vo.setFollowCount(bar.getFollowCount() == null ? 0 : bar.getFollowCount());
        vo.setPostCount(bar.getPostCount() == null ? 0 : bar.getPostCount());
        vo.setOwnerUserId(bar.getOwnerUserId());
        vo.setOwnerNickName(owner == null ? null : owner.getNickName());
        vo.setStatus(bar.getStatus());
        vo.setCreateTime(bar.getCreateTime());
        return vo;
    }

    private void requirePlatformAdmin(Long operatorId) {
        if (!permissionSupport.isPlatformAdmin(operatorId)) {
            throw new BusinessException("仅平台管理员可访问");
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
