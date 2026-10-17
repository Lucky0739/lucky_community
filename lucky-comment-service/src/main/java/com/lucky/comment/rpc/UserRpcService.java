package com.lucky.comment.rpc;

import com.lucky.common.Result;
import com.lucky.user.api.api.UserFeignApi;
import com.lucky.user.api.dto.FindUsersByIdsDTO;
import com.lucky.user.api.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户服务 RPC 封装。
 */
@Component
@RequiredArgsConstructor
public class UserRpcService {

    private final UserFeignApi userFeignApi;

    /**
     * 批量查询用户昵称和头像。入参为空或查询无结果时返回 null。
     */
    public List<UserBriefVO> findByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        FindUsersByIdsDTO request = FindUsersByIdsDTO.builder()
                .ids(userIds.stream().distinct().toList())
                .build();
        Result<List<UserBriefVO>> result = userFeignApi.findByIds(request);
        if (result == null || !Boolean.TRUE.equals(result.getSuccess())
                || result.getData() == null || result.getData().isEmpty()) {
            return null;
        }
        return result.getData();
    }
}
