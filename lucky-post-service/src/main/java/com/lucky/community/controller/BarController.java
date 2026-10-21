package com.lucky.community.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.file.FileService;
import com.lucky.community.dto.BarQueryDTO;
import com.lucky.community.dto.CreateBarDTO;
import com.lucky.community.dto.OwnedBarQueryDTO;
import com.lucky.community.dto.UpdateBarDTO;
import com.lucky.community.service.BarService;
import com.lucky.community.vo.BarVO;
import com.lucky.common.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Slf4j
@RestController
@RequestMapping("/bar")
@RequiredArgsConstructor
public class BarController {

    private final BarService barService;
    private final FileService fileService;

    /**
     * 建贴吧
     * @param createBarDTO
     * @return
     */
    @AuthRequired
    @PostMapping("/create")
    public Result<BarVO> create(@RequestBody @Valid CreateBarDTO createBarDTO) {
        return Result.success(barService.createBar(UserContext.getUserId(), createBarDTO));
    }

    /**
     * 上传贴吧的封面图（贴吧吧头像）
     * @param file
     * @return
     */
    @AuthRequired
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_BAR));
    }

    /**
     * 编辑修改贴吧
     * @param barId
     * @param updateBarDTO
     * @return
     */
    @AuthRequired
    @PutMapping("/{barId}")
    public Result<BarVO> update(@PathVariable("barId") Long barId,
                                @RequestBody @Valid UpdateBarDTO updateBarDTO) {
        return Result.success(barService.updateBar(UserContext.getUserId(), barId, updateBarDTO));
    }

    /**
     * 删除/停用贴吧
     * @param barId
     * @return
     */
    @AuthRequired
    @DeleteMapping("/{barId}")
    public Result<String> delete(@PathVariable("barId") Long barId) {
        barService.deleteBar(UserContext.getUserId(), barId);
        return Result.success();
    }

    /**
     * 分页查询贴吧。未登录可看吧和最新帖；带 token 时附带是否已关注。
     */
    @GetMapping("/page")
    public Result<List<BarVO>> page(@ModelAttribute @Valid BarQueryDTO barQueryDTO) {
        PageResult<BarVO> page = barService.pageBars(barQueryDTO);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 用户创建的贴吧列表（含各吧粉丝数）。
     */
    @GetMapping("/owned/page")
    public Result<List<BarVO>> ownedPage(@ModelAttribute @Valid OwnedBarQueryDTO query) {
        PageResult<BarVO> page = barService.pageOwnedBars(UserContext.getUserId(), query);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 查看贴吧详情
     * @param barId
     * @return
     */
    @GetMapping("/{barId}")
    public Result<BarVO> detail(@PathVariable("barId") Long barId) {
        return Result.success(barService.getBarDetail(barId));
    }
}
