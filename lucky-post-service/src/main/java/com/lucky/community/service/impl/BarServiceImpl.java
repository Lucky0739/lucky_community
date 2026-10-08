package com.lucky.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.bloom.LongBloomFilter;
import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.file.FileService;
import com.lucky.community.cache.BarDetailCache;
import com.lucky.community.cache.BarPageCache;
import com.lucky.community.dto.BarQueryDTO;
import com.lucky.community.dto.CreateBarDTO;
import com.lucky.community.dto.UpdateBarDTO;
import com.lucky.community.entity.Bar;
import com.lucky.community.entity.BarManager;
import com.lucky.community.mapper.BarManagerMapper;
import com.lucky.community.mapper.BarMapper;
import com.lucky.community.service.BarService;
import com.lucky.community.service.PostService;
import com.lucky.community.vo.BarVO;
import com.lucky.community.vo.PostSummaryVO;
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
    private final BarPageCache barPageCache;
    private final FileService fileService;
    private final LongBloomFilter barBloomFilter;
    private final PostService postService;

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
        // 默认为1（正常状态）
        bar.setStatus(Bar.STATUS_NORMAL);
        try {
            // 新增插入数据库
            barMapper.insert(bar);
        } catch (DuplicateKeyException e) {
            // 数据库设置唯一贴吧名
            throw new BusinessException("吧名已被占用：" + name);
        }
        // 放入布隆过滤器
        barBloomFilter.add(bar.getId());
        BarManager barManager = new BarManager();
        barManager.setBarId(bar.getId());
        barManager.setUserId(userId);
        barManager.setGrantUserId(userId);
        barManager.setStatus(BarManager.STATUS_ACTIVE);
        // 新增插入贴吧-用户对应的数据
        barManagerMapper.insert(barManager);
        // 给创建帖吧的用户增加吧务角色身份
        permissionSupport.bindRoleIfAbsent(userId, RoleType.BAR_ADMIN);
        Bar saved = barMapper.selectById(bar.getId());
        if (saved == null) {
            saved = bar;
        }
        // 查询用户id，昵称和头像
        UserBrief userBrief= userReadMapper.selectById(userId);
        // 事务提交后执行
        registerAfterCommit(barPageCache::invalidate);
        return toBarVO(saved, userBrief);
    }

    /**
     * 修改吧
     * @param operatorId
     * @param barId
     * @param updateBarDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BarVO updateBar(Long operatorId, Long barId, UpdateBarDTO updateBarDTO) {
        // 根据barId查询
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            throw new BusinessException("贴吧不存在");
        }
        if (!Objects.equals(operatorId, bar.getOwnerUserId())) {
            throw new BusinessException("只能修改自己发布的贴吧");
        }
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
        // 事务执行完之后写入缓存
        registerAfterCommit(() -> {
            barDetailCache.evict(barId);
            barPageCache.invalidate();
        });
        BarVO barVO = loadBarVO(barId);
        if (barVO == null) {
            throw new BusinessException("贴吧不存在");
        }
        return barVO;
    }

    /**
     * 删除帖吧
     * @param operatorId
     * @param barId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBar(Long operatorId, Long barId) {
        // 检验删帖吧的用户是否吧主或者管理员
        Bar bar = requireManageable(operatorId, barId, "仅吧主或平台管理员可删除贴吧");
        if (bar.getStatus() != null && bar.getStatus() == Bar.STATUS_DISABLED) {
            throw new BusinessException("贴吧已停用");
        }
        barMapper.updateStatus(barId, Bar.STATUS_DISABLED);
        registerAfterCommit(() -> {
            barDetailCache.evict(barId);
            barPageCache.invalidate();
        });
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

    /** 贴吧列表每页条数固定，不采用请求里的 pageSize，避免首页缓存按条数被打散。 */
    private static final int BAR_PAGE_SIZE = 10;

    /**
     * 分页查询吧
     * 无关键词的首页和总数走本机缓存，挡高并发下的 COUNT / 首屏风暴；
     * 翻页若已经超过总数则不再打 SELECT。关键词搜索不缓存——组合无穷，命中率趋近于零。
     * 页码由请求传入，每页条数固定为 {@link #BAR_PAGE_SIZE}。
     */
    @Override
    public PageResult<BarVO> pageBars(BarQueryDTO barQueryDTO) {
        int pageNum = barQueryDTO.getPageNum() == null ? 1 : barQueryDTO.getPageNum() ;
        int pageSize = BAR_PAGE_SIZE;
        String keyword = StringUtils.hasText(barQueryDTO.getKeyword()) ? barQueryDTO.getKeyword().trim() : null;
        long total;
        List<BarVO> records;
        if (keyword == null) {
            total = barPageCache.getTotal(() -> barMapper.countPage(Bar.STATUS_NORMAL, null));
            records = loadUnfilteredPage(pageNum, pageSize, total);
        } else {
            total = barMapper.countPage(Bar.STATUS_NORMAL, keyword);
            records = loadRecords(keyword, pageSize, (pageNum - 1) * pageSize, total);
        }
        attachLatestPost(records);
        PageResult<BarVO> result = new PageResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    /** 吧列表只挂每个吧最新一条帖。完整列表必须进吧后再查。 */
    private void attachLatestPost(List<BarVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> barIds = records.stream().map(BarVO::getBarId).toList();
        Map<Long, PostSummaryVO> latest = postService.latestVisibleByBarIds(barIds);
        for (BarVO bar : records) {
            bar.setLatestPost(latest.get(bar.getBarId()));
        }
    }

    private List<BarVO> loadUnfilteredPage(int pageNum, int pageSize, long total) {
        int offset = (pageNum - 1) * pageSize;
        if (offset >= total) {
            return List.of();
        }
        if (pageNum == 1) {
            return barPageCache.getFirstPage(pageSize, () -> queryRecords(null, pageSize, 0));
        }
        return queryRecords(null, pageSize, offset);
    }

    private List<BarVO> loadRecords(String keyword, int pageSize, int offset, long total) {
        if (offset >= total) {
            return List.of();
        }
        return queryRecords(keyword, pageSize, offset);
    }

    private List<BarVO> queryRecords(String keyword, int pageSize, int offset) {
        List<Bar> bars = barMapper.selectPage(Bar.STATUS_NORMAL, keyword, pageSize, offset);
        if (bars.isEmpty()) {
            return List.of();
        }
        return toVOs(bars);
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

    /**
     * 回源查库
     */
    private BarVO loadBarVO(Long barId) {
        Bar bar = barMapper.selectById(barId);
        if (bar == null) {
            return null;
        }
        // 库里存在却回源了，说明布隆可能漏了它（过滤器丢失/未初始化/add 曾失败降级）→ 补回，下次直接命中
        barBloomFilter.backfill(barId);
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
