package com.lucky.core.bar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lucky.core.bar.cache.BarDetailCache;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lucky.core.bar.dto.BarQueryDTO;
import com.lucky.core.bar.dto.CreateBarDTO;
import com.lucky.core.bar.dto.UpdateBarDTO;
import com.lucky.core.bar.entity.Bar;
import com.lucky.core.bar.entity.BarManager;
import com.lucky.core.user.entity.User;
import com.lucky.core.bar.mapper.BarManagerMapper;
import com.lucky.core.bar.mapper.BarMapper;
import com.lucky.core.user.mapper.UserMapper;
import com.lucky.core.bar.service.BarService;
import com.lucky.core.file.FileService;
import com.lucky.core.user.service.PermissionService;
import com.lucky.core.bar.vo.BarVO;
import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
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
    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final BarDetailCache barDetailCache;
    private final FileService fileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BarVO createBar(Long userId, CreateBarDTO createBarDTO) {
        String name = createBarDTO.getName().trim();
        Bar bar = new Bar();
        bar.setName(name);
        bar.setDescription(createBarDTO.getDescription());
        bar.setOwnerUserId(userId);
        bar.setFollowCount(0);
        bar.setAvatar(createBarDTO.getAvatar());
        bar.setStatus(Bar.STATUS_NORMAL);
        try {
            barMapper.insert(bar);   // MP 回填雪花ID
        } catch (DuplicateKeyException e) {
            throw new BusinessException("吧名已被占用：" + name);
        }
        // 创建者即吧主。必须写 tb_bar_manager 而不只是 owner_user_id：
        BarManager barManager = new BarManager();
        barManager.setBarId(bar.getId());
        barManager.setUserId(userId);
        barManager.setGrantUserId(userId);
        barManager.setStatus(BarManager.STATUS_ACTIVE);
        barManagerMapper.insert(barManager);
        permissionService.bindRoleIfAbsent(userId, RoleType.BAR_ADMIN);
        // 重查一次拿 DB 填充的 create_time / update_time：MP 的 insert 不会回读数据库默认值，
        Bar saved = barMapper.selectById(bar.getId());
        return toVO(saved == null ? bar : saved, userMapper.selectById(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BarVO updateBar(Long operatorId, Long barId, UpdateBarDTO dto) {
        Bar bar = requireManageable(operatorId, barId, "仅吧主或平台管理员可修改贴吧");
        String newName = null;
        if (dto.getName() != null) {
            newName = dto.getName().trim();
            if (!StringUtils.hasText(newName)) {
                throw new BusinessException("吧名不能为空");
            }
            if (newName.equals(bar.getName())) {
                newName = null;
            }
        }
        boolean changeDescription = dto.getDescription() != null;
        boolean changeAvatar = dto.getAvatar() != null;
        if (newName == null && !changeDescription && !changeAvatar) {
            throw new BusinessException("没有要修改的内容");
        }
        LambdaUpdateWrapper<Bar> update = new LambdaUpdateWrapper<Bar>().eq(Bar::getId, barId);
        if (newName != null) {
            update.set(Bar::getName, newName);
        }
        if (changeDescription) {
            update.set(Bar::getDescription, dto.getDescription());
        }
        if (changeAvatar) {
            String avatar = dto.getAvatar();
            if (StringUtils.hasText(avatar) && fileService.resolveBarObjectName(avatar) == null) {
                throw new BusinessException("吧头像地址不合法");
            }
            update.set(Bar::getAvatar, avatar);
        }
        try {
            barMapper.update(null, update);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("吧名已被占用：" + newName);
        }
        registerAfterCommit(() -> barDetailCache.evict(barId));
        BarVO vo = loadBarVO(barId);
        if (vo == null) {
            throw new BusinessException("贴吧不存在");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBar(Long operatorId, Long barId) {
        Bar bar = requireManageable(operatorId, barId, "仅吧主或平台管理员可删除贴吧");
        if (bar.getStatus() != null && bar.getStatus() == Bar.STATUS_DISABLED) {
            throw new BusinessException("贴吧已停用");
        }
        barMapper.update(null, new LambdaUpdateWrapper<Bar>()
                .eq(Bar::getId, barId)
                .set(Bar::getStatus, Bar.STATUS_DISABLED));
        registerAfterCommit(() -> barDetailCache.evict(barId));
    }

    /**
     * 吧主或平台管理员才能改吧、删吧。吧务只能处置帖子，不能动吧本身。
     */
    private Bar requireManageable(Long operatorId, Long barId, String deniedMessage) {
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            throw new BusinessException("贴吧不存在");
        }
        if (operatorId.equals(bar.getOwnerUserId()) || permissionService.isPlatformAdmin(operatorId)) {
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

    /**
     * 分页查吧
     * <p>原本还有一条「按热度榜取一页」的路径（读 {@code bar:hot} ZSet），热度功能摘除后
     * 统一走 PostgreSQL。代价是排序从「帖子数倒序」变成了 {@code id} 倒序，见 {@link #pageBarsFromDb}
     */
    @Override
    public IPage<BarVO> pageBars(BarQueryDTO barQueryDTO) {
        if (StringUtils.hasText(barQueryDTO.getKeyword())) {
            return pageBarsByKeyword(barQueryDTO);
        }
        return pageBarsFromDb(barQueryDTO);
    }

    /**
     * 分页查询板块
     * @param barQueryDTO
     * @return
     */
    private IPage<BarVO> pageBarsByKeyword(BarQueryDTO barQueryDTO) {
        LambdaQueryWrapper<Bar> wrapper = new LambdaQueryWrapper<Bar>()
                // 停用的吧不出现在列表里；详情接口则不过滤，见 BarService#getBar
                .eq(Bar::getStatus, Bar.STATUS_NORMAL)
                .likeRight(Bar::getName, barQueryDTO.getKeyword())
                .orderByDesc(Bar::getId);
        return toPage(barsPage(barQueryDTO, wrapper));
    }

    // 吧分页查询数据库（多级缓存）
    private IPage<BarVO> pageBarsFromDb(BarQueryDTO barQueryDTO) {
        LambdaQueryWrapper<Bar> wrapper = new LambdaQueryWrapper<Bar>()
                .eq(Bar::getStatus, Bar.STATUS_NORMAL)
                .orderByDesc(Bar::getId);
        return toPage(barsPage(barQueryDTO, wrapper));
    }

    private Page<Bar> barsPage(BarQueryDTO query, LambdaQueryWrapper<Bar> wrapper) {
        return barMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    private IPage<BarVO> toPage(Page<Bar> page) {
        Page<BarVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            // 本页无数据时跳过吧主查询
            return result;
        }
        result.setRecords(toVOs(page.getRecords()));
        return result;
    }

    /**
     * 实体转 VO，并批量补齐吧主昵称
     * <p>一次 IN 查回所有吧主，避免逐个查库(N+1)；必须过滤 null——{@code owner_user_id} 可空，
     * 而 {@code selectBatchIds} 传空集合会拼出 {@code IN ()} 这种语法错误的 SQL
     */
    private List<BarVO> toVOs(List<Bar> bars) {
        Set<Long> ownerIds = bars.stream()
                .map(Bar::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> ownerById = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(ownerIds)) {
                ownerById.put(user.getId(), user);
            }
        }
        return bars.stream()
                .map(bar -> toVO(bar, ownerById.get(bar.getOwnerUserId())))
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

    /**
     * 回源：查库组装 BarVO。只在缓存未命中时被 {@link BarDetailCache} 回调
     */
    private BarVO loadBarVO(Long barId) {
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            return null;
        }
        Long ownerUserId = bar.getOwnerUserId();
        return toVO(bar, ownerUserId == null ? null : userMapper.selectById(ownerUserId));
    }

    private BarVO toVO(Bar bar, User owner) {
        BarVO barVO = new BarVO();
        barVO.setBarId(bar.getId());
        barVO.setName(bar.getName());
        barVO.setDescription(bar.getDescription());
        barVO.setAvatar(bar.getAvatar());
        barVO.setFollowCount(bar.getFollowCount() == null ? 0 : bar.getFollowCount());
        barVO.setPostCount(bar.getPostCount() == null ? 0 : bar.getPostCount());
        barVO.setOwnerUserId(bar.getOwnerUserId());
        // 吧主已注销时为 null，不让整个查询失败
        barVO.setOwnerNickName(owner == null ? null : owner.getNickName());
        barVO.setStatus(bar.getStatus());
        barVO.setCreateTime(bar.getCreateTime());
        return barVO;
    }
}
