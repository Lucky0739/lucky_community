package com.lucky.community.bar.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.file.FileService;
import com.lucky.community.bar.dto.BarQueryDTO;
import com.lucky.community.bar.dto.CreateBarDTO;
import com.lucky.community.bar.dto.UpdateBarDTO;
import com.lucky.community.bar.service.BarService;
import com.lucky.community.bar.vo.BarVO;
import com.lucky.community.common.PageResult;
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

@Slf4j
@RestController
@RequestMapping("/bar")
@AuthRequired
@RequiredArgsConstructor
public class BarController {

    private final BarService barService;
    private final FileService fileService;

    @PostMapping
    public Result<BarVO> create(@RequestBody @Valid CreateBarDTO createBarDTO) {
        return Result.success(barService.createBar(UserContext.getUserId(), createBarDTO));
    }

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_BAR));
    }

    @PutMapping("/{barId}")
    public Result<BarVO> update(@PathVariable("barId") Long barId,
                                @RequestBody @Valid UpdateBarDTO updateBarDTO) {
        return Result.success(barService.updateBar(UserContext.getUserId(), barId, updateBarDTO));
    }

    @DeleteMapping("/{barId}")
    public Result<String> delete(@PathVariable("barId") Long barId) {
        barService.deleteBar(UserContext.getUserId(), barId);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<java.util.List<BarVO>> page(@ModelAttribute @Valid BarQueryDTO barQueryDTO) {
        PageResult<BarVO> page = barService.pageBars(barQueryDTO);
        return Result.success(page.getRecords(), page.getTotal());
    }

    @GetMapping("/{barId}")
    public Result<BarVO> detail(@PathVariable("barId") Long barId) {
        return Result.success(barService.getBar(barId));
    }
}
