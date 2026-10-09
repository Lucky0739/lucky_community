package com.lucky.community.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.vo.PageResult;
import com.lucky.community.dto.AdminBarQueryDTO;
import com.lucky.community.dto.AdminBarStatusDTO;
import com.lucky.community.service.AdminBarService;
import com.lucky.community.vo.BarVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台管理员管贴吧：分页查看全部吧，停用或恢复。
 */
@RestController
@RequestMapping("/admin/bar")
@AuthRequired
@RequiredArgsConstructor
public class AdminBarController {

    private final AdminBarService adminBarService;

    /**
     * 分页查询贴吧
     * @param adminBarQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<List<BarVO>> page(@ModelAttribute @Valid AdminBarQueryDTO adminBarQueryDTO) {
        PageResult<BarVO> page = adminBarService.pageBars(UserContext.getUserId(), adminBarQueryDTO);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 停用恢复贴吧
     * @param adminBarStatusDTO
     * @return
     */
    @PutMapping("/status")
    public Result<String> updateStatus(@RequestBody @Valid AdminBarStatusDTO adminBarStatusDTO) {
        adminBarService.updateStatus(UserContext.getUserId(), adminBarStatusDTO);
        return Result.success();
    }

    /**
     * 删除贴吧
     */
    @DeleteMapping("/{barId}")
    public Result<String> delete(@PathVariable("barId") Long barId) {
        adminBarService.deleteBar(UserContext.getUserId(), barId);
        return Result.success();
    }
}
