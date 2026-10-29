package com.lucky.interaction.service;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.FollowListQueryDTO;
import com.lucky.interaction.vo.BarFollowerVO;
import com.lucky.interaction.vo.FollowBarSnapshotVO;
import com.lucky.interaction.vo.FollowBarVO;

import java.util.List;

public interface FollowService {

    /**
     * 关注贴吧：先确认贴吧存在，再 Lua 原子写入 Redis，成功后投递 MQ 落库
     */
    void follow(Long userId, Long barId);

    /**
     * 当前用户是否已关注该贴吧。热路径看 Redis，ZSet 不存在时回源 tb_follow。
     */
    boolean isFollowed(Long userId, Long barId);

    /**
     * 取消关注：Lua 原子删 Redis，成功后投递 MQ 删 tb_follow
     */
    void unfollow(Long userId, Long barId);

    /**
     * 关注的贴吧列表。可按吧名关键词分页。
     */
    PageResult<FollowBarVO> pageFollowees(Long viewerId, FollowListQueryDTO query);

    /** 按 id 批量返回贴吧展示字段及 viewer 是否已关注；顺序与入参一致，跳过不存在的吧 */
    List<FollowBarSnapshotVO> listBarSnapshots(Long viewerId, List<Long> barIds);

    /** 某个贴吧的粉丝（关注该吧的用户）分页 */
    PageResult<BarFollowerVO> pageBarFollowers(Long barId, int pageNum, int pageSize);
}
