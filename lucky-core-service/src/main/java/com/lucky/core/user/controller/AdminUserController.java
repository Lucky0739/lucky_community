package com.lucky.core.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lucky.common.Result;
import com.lucky.core.auth.annotation.AuthRequired;
import com.lucky.core.user.dto.AdminUserQueryDTO;
import com.lucky.core.user.dto.BatchDeleteUserDTO;
import com.lucky.core.user.dto.UpdateProfileDTO;
import com.lucky.core.user.service.AdminUserService;
import com.lucky.core.auth.UserContext;
import com.lucky.core.user.vo.AdminUserDetailVO;
import com.lucky.core.user.vo.AdminUserVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员用户管理
 */
@Slf4j
@RestController
@RequestMapping("/admin/user")
@AuthRequired
public class AdminUserController {

    @Resource
    private AdminUserService adminUserService;

    /**
     * 分页查询用户
     */
    @GetMapping("/page")
    public Result<List<AdminUserVO>> page(@ModelAttribute @Valid AdminUserQueryDTO query) {
        IPage<AdminUserVO> page = adminUserService.pageUsers(UserContext.getUserId(), query);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 用户详情：基本信息 + 扩展资料 + 角色
     */
    @GetMapping("/{userId}")
    public Result<AdminUserDetailVO> detail(@PathVariable("userId") Long userId) {
        return Result.success(adminUserService.getUserDetail(UserContext.getUserId(), userId));
    }
    /**
     * 编辑用户资料
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid UpdateProfileDTO dto) {
        adminUserService.updateUser(UserContext.getUserId(), dto);
        return Result.success();
    }
    /**
     * 逻辑删除用户，单次上限 100
     */
    @DeleteMapping
    public Result<String> delete(@RequestBody @Valid BatchDeleteUserDTO dto) {
        adminUserService.deleteUsers(UserContext.getUserId(), dto.getUserIds());
        return Result.success();
    }
}
