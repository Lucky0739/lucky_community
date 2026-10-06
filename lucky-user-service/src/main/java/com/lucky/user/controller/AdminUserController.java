package com.lucky.user.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.user.dto.AdminUpdateProfileDTO;
import com.lucky.user.dto.AdminUserQueryDTO;
import com.lucky.user.dto.AppointBarAdminDTO;
import com.lucky.user.dto.BatchDeleteUserDTO;
import com.lucky.user.dto.HandlerActionDTO;
import com.lucky.user.service.AdminUserService;
import com.lucky.user.vo.AdminUserDetailVO;
import com.lucky.user.vo.AdminUserVO;
import com.lucky.common.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 分页查询用户
     */
    @GetMapping("/page")
    public Result<List<AdminUserVO>> page(@ModelAttribute @Valid AdminUserQueryDTO query) {
        PageResult<AdminUserVO> page = adminUserService.pageUsers(UserContext.getUserId(), query);
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
     * 编辑用户资料：请求体里的 userId 是「被编辑的人」，操作人取 access token
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid AdminUpdateProfileDTO dto) {
        adminUserService.updateUser(UserContext.getUserId(), dto);
        return Result.success();
    }

    /**
     * 任命吧务：写入任命记录并给目标用户绑定 BAR_ADMIN 角色
     */
    @PostMapping("/bar-admin/appoint")
    public Result<String> appointBarAdmin(@RequestBody @Valid AppointBarAdminDTO dto) {
        adminUserService.appointBarAdmin(UserContext.getUserId(), dto);
        return Result.success();
    }

    /**
     * 人工处置一条帖子。结论写入 handler_action：未处置、警告、删帖、封禁。
     */
    @PostMapping("/handler-action")
    public Result<String> reviewPost(@RequestBody @Valid HandlerActionDTO dto) {
        adminUserService.reviewPost(UserContext.getUserId(), dto);
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
