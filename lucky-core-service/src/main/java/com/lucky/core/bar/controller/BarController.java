package com.lucky.core.bar.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lucky.common.Result;
import com.lucky.core.auth.annotation.AuthRequired;
import com.lucky.core.bar.dto.BarQueryDTO;
import com.lucky.core.bar.dto.CreateBarDTO;
import com.lucky.core.bar.dto.UpdateBarDTO;
import com.lucky.core.bar.service.BarService;
import com.lucky.core.file.FileService;
import com.lucky.core.auth.UserContext;
import com.lucky.core.bar.vo.BarVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 贴吧
 */
@Slf4j
@RestController
@RequestMapping("/bar")
@AuthRequired
public class BarController {

    @Resource
    private BarService barService;

    @Resource
    private FileService fileService;

    /**
     * 建吧：创建者自动成为吧主
     */
    @PostMapping
    public Result<BarVO> create(@RequestBody @Valid CreateBarDTO createBarDTO) {
        return Result.success(barService.createBar(UserContext.getUserId(), createBarDTO));
    }

    /**
     * 上传吧头像：文件存 MinIO 的 bar/ 目录，返回可访问 URL。
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_BAR));
    }

    /**
     * 修改贴吧：吧名、简介、头像。仅吧主或平台管理员。
     */
    @PutMapping("/{barId}")
    public Result<BarVO> update(@PathVariable("barId") Long barId,
                                @RequestBody @Valid UpdateBarDTO updateBarDTO) {
        return Result.success(barService.updateBar(UserContext.getUserId(), barId, updateBarDTO));
    }

    /**
     * 删吧：状态改为停用，列表中不再出现。仅吧主或平台管理员。
     */
    @DeleteMapping("/{barId}")
    public Result<String> delete(@PathVariable("barId") Long barId) {
        barService.deleteBar(UserContext.getUserId(), barId);
        return Result.success();
    }

    /**
     * 分页查吧 / 搜吧
     */
    @GetMapping("/page")
    public Result<List<BarVO>> page(@ModelAttribute @Valid BarQueryDTO barQueryDTO) {
        IPage<BarVO> page = barService.pageBars(barQueryDTO);
        return Result.success(page.getRecords(), page.getTotal());
    }
    /**
     * 吧详情
     */
    @GetMapping("/{barId}")
    public Result<BarVO> detail(@PathVariable("barId") Long barId) {
        return Result.success(barService.getBar(barId));
    }
}
