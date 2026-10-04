package com.lucky.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.bloom.LongBloomFilter;
import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.file.FileService;
import com.lucky.community.cache.BarDetailCache;
import com.lucky.community.dto.BarQueryDTO;
import com.lucky.community.dto.CreateBarDTO;
import com.lucky.community.dto.UpdateBarDTO;
import com.lucky.community.entity.Bar;
import com.lucky.community.entity.BarManager;
import com.lucky.community.mapper.BarManagerMapper;
import com.lucky.community.mapper.BarMapper;
import com.lucky.community.service.BarService;
import com.lucky.community.vo.BarVO;
import com.lucky.common.vo.PageResult;
import com.lucky.community.support.PermissionSupport;
import com.lucky.community.support.entity.UserBrief;
import com.lucky.community.support.mapper.UserReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
public class BarServiceImpl implements BarService {

    private final BarMapper barMapper;
    private final BarManagerMapper barManagerMapper;
    private final UserReadMapper userReadMapper;
    private final PermissionSupport permissionSupport;
    private final BarDetailCache barDetailCache;
    private final FileService fileService;
    private final LongBloomFilter barBloomFilter;

    /**
     * 创建吧
     * @param userId
     * @param createBarDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BarVO createBar(Long userId, CreateBarDTO createBarDTO) {
        String name = createBarDTO.getName().trim();
        Bar bar = new Bar();
        bar.setId(IdUtil.getSnowflakeNextId());
        bar.setName(name);
        bar.setDescription(createBarDTO.getDescription());
        bar.setOwnerUserId(userId);
        bar.setFollowCount(0);
        bar.setAvatar(createBarDTO.getAvatar());
        bar.setStatus(Bar.STATUS_NORMAL);
        try {
            barMapper.insert(bar);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("吧名已被占用：" + name);
        }
        barBloomFilter.add(bar.getId());
        BarManager barManager = new BarManager();
        barManager.setBarId(bar.getId());
        barManager.setUserId(userId);
        barManager.setGrantUserId(userId);
        barManager.setStatus(BarManager.STATUS_ACTIVE);
        barManagerMapper.insert(barManager);
        permissionSupport.bindRoleIfAbsent(userId, RoleType.BAR_ADMIN);
        Bar saved = barMapper.selectById(bar.getId());
        if (saved == null) {
            saved = bar;
        }
        UserBrief userBrief= userReadMapper.selectById(userId);
        return toBarVO(saved, userBrief);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BarVO updateBar(Long operatorId, Long barId, UpdateBarDTO updateBarDTO) {
        Bar bar = requireManageable(operatorId, barId, "仅吧主或平台管理员可修改贴吧");
        String newName = null;
        if (updateBarDTO.getName() != null) {
            newName = updateBarDTO.getName().trim();
            if (!StringUtils.hasText(newName)) {
                throw new BusinessException("吧名不能为空");
            }
            if (newName.equals(bar.getName())) {
                newName = null;
            }
        }
        boolean changeDescription = updateBarDTO.getDescription() != null;
        boolean changeAvatar = updateBarDTO.getAvatar() != null;
        if (newName == null && !changeDescription && !changeAvatar) {
            throw new BusinessException("没有要修改的内容");
        }
        if (changeAvatar) {
            String avatar = updateBarDTO.getAvatar();
            if (StringUtils.hasText(avatar) && fileService.resolveBarObjectName(avatar) == null) {
                throw new BusinessException("吧头像地址不合法");
            }
        }
        Bar patch = new Bar();
        patch.setId(barId);
        patch.setName(newName);
        if (changeDescription) {
            patch.setDescription(updateBarDTO.getDescription());
        }
        if (changeAvatar) {
            patch.setAvatar(updateBarDTO.getAvatar());
        }
        try {
            barMapper.updateById(patch);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("吧名已被占用：" + newName);
        }
        registerAfterCommit(() -> barDetailCache.evict(barId));
        BarVO barVO = loadBarVO(barId);
        if (barVO == null) {
            throw new BusinessException("贴吧不存在");
        }
        return barVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBar(Long operatorId, Long barId) {
        Bar bar = requireManageable(operatorId, barId, "仅吧主或平台管理员可删除贴吧");
        if (bar.getStatus() != null && bar.getStatus() == Bar.STATUS_DISABLED) {
            throw new BusinessException("贴吧已停用");
        }
        barMapper.updateStatus(barId, Bar.STATUS_DISABLED);
        registerAfterCommit(() -> barDetailCache.evict(barId));
    }

    private Bar requireManageable(Long operatorId, Long barId, String deniedMessage) {
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            throw new BusinessException("贴吧不存在");
        }
        if (operatorId.equals(bar.getOwnerUserId()) || permissionSupport.isPlatformAdmin(operatorId)) {
            return bar;
        }
        throw new BusinessException(deniedMessage);
    }

    private void registerAfterCommit(Runnable action) {
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

    @Override
    public PageResult<BarVO> pageBars(BarQueryDTO barQueryDTO) {
        int pageNum = barQueryDTO.getPageNum() == null ? 1 : barQueryDTO.getPageNum();
        int pageSize = barQueryDTO.getPageSize() == null ? 10 : barQueryDTO.getPageSize();
        String keyword = StringUtils.hasText(barQueryDTO.getKeyword()) ? barQueryDTO.getKeyword().trim() : null;
        long total = barMapper.countPage(Bar.STATUS_NORMAL, keyword);
        int offset = (pageNum - 1) * pageSize;
        List<Bar> bars = barMapper.selectPage(Bar.STATUS_NORMAL, keyword, pageSize, offset);
        PageResult<BarVO> result = new PageResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(total);
        if (bars.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }
        result.setRecords(toVOs(bars));
        return result;
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

    @Override
    public BarVO getBar(Long barId) {
        BarVO vo = barDetailCache.get(barId, this::loadBarVO);
        if (vo == null) {
            throw new BusinessException("贴吧不存在");
        }
        return vo;
    }

    private BarVO loadBarVO(Long barId) {
        if (!barBloomFilter.mightContain(barId)) {
            return null;
        }
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            return null;
        }
        Long ownerUserId = bar.getOwnerUserId();
        return toBarVO(bar, ownerUserId == null ? null : userReadMapper.selectById(ownerUserId));
    }

    private BarVO toBarVO(Bar bar, UserBrief userBrief) {
        BarVO barVO = new BarVO();
        barVO.setBarId(bar.getId());
        barVO.setName(bar.getName());
        barVO.setDescription(bar.getDescription());
        barVO.setAvatar(bar.getAvatar());
        barVO.setFollowCount(bar.getFollowCount() == null ? 0 : bar.getFollowCount());
        barVO.setPostCount(bar.getPostCount() == null ? 0 : bar.getPostCount());
        barVO.setOwnerUserId(bar.getOwnerUserId());
        barVO.setOwnerNickName(userBrief == null ? null : userBrief.getNickName());
        barVO.setStatus(bar.getStatus());
        barVO.setCreateTime(bar.getCreateTime());
        return barVO;
    }
}
