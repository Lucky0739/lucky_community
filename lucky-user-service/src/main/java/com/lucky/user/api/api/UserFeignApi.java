package com.lucky.user.api.api;

import com.lucky.common.Result;
import com.lucky.user.api.constant.ApiConstants;
import com.lucky.user.api.dto.FindUsersByIdsDTO;
import com.lucky.user.api.vo.UserBriefVO;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 作者昵称和头像。帖子详情调用，不要求登录。
 */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface UserFeignApi {

    String PREFIX = "/user";

    @GetMapping(PREFIX + "/brief/{userId}")
    Result<UserBriefVO> brief(@PathVariable("userId") Long userId);

    @PostMapping(PREFIX + "/brief/batch")
    Result<List<UserBriefVO>> findByIds(@RequestBody @Valid FindUsersByIdsDTO findUsersByIdsDTO);
}
