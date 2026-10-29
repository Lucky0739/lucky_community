package com.lucky.interaction.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.UserContext;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.FollowListQueryDTO;
import com.lucky.interaction.service.FollowFeedCardQueryService;
import com.lucky.interaction.service.FollowFeedService;
import com.lucky.interaction.service.FollowService;
import com.lucky.interaction.vo.BarFollowerVO;
import com.lucky.interaction.vo.FollowBarSnapshotVO;
import com.lucky.interaction.vo.FollowBarVO;
import com.lucky.interaction.vo.FollowFeedCardVO;
import com.lucky.interaction.vo.FollowFeedItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/follow")
@AuthRequired
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final FollowFeedService followFeedService;
    private final FollowFeedCardQueryService followFeedCardQueryService;

    /**
     * 关注指定贴吧
     */
    @PostMapping("/{barId}")
    public Result<String> follow(@PathVariable("barId") Long barId) {
        followService.follow(UserContext.getUserId(), barId);
        return Result.success("关注成功");
    }

    /**
     * 关注的贴吧列表。可按吧名关键词分页。
     */
    @GetMapping("/page")
    public Result<List<FollowBarVO>> page(@ModelAttribute @Valid FollowListQueryDTO followListQueryDTO) {
        PageResult<FollowBarVO> page = followService.pageFollowees(UserContext.getUserId(), followListQueryDTO);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 某个贴吧的粉丝列表（关注该吧的用户）。
     */
    @GetMapping("/bar/{barId}/fans/page")
    public Result<List<BarFollowerVO>> barFans(@PathVariable("barId") Long barId,
                                               @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                               @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        PageResult<BarFollowerVO> page = followService.pageBarFollowers(barId, pageNum, pageSize);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 查看关注流。小吧的帖子由发帖时推入，大吧的帖子要先调用拉取接口。
     */
    @GetMapping("/feed")
    public Result<List<FollowFeedItemVO>> feed(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        PageResult<FollowFeedItemVO> page = followFeedService.page(UserContext.getUserId(), pageNo, pageSize);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 首页关注流卡片：Redis 关注流顺序 + 帖子/贴吧/互动数读库组装。
     */
    @GetMapping("/feed/cards")
    public Result<List<FollowFeedCardVO>> feedCards(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        PageResult<FollowFeedCardVO> page =
                followFeedCardQueryService.pageCards(UserContext.getUserId(), pageNo, pageSize);
        return Result.success(page.getRecords(), page.getTotal());
    }

    /**
     * 把关注的大吧最近帖子拉进自己的 Redis 关注流。
     */
    @PostMapping("/feed/pull")
    public Result<Integer> pull() {
        return Result.success(followFeedService.pull(UserContext.getUserId()));
    }

    /**
     * 按 barId 批量查贴吧名、粉丝数、帖子数及当前用户是否已关注（首页方案一补全字段）。
     */
    @GetMapping("/bars/meta")
    public Result<List<FollowBarSnapshotVO>> barMeta(@RequestParam("barIds") List<Long> barIds) {
        return Result.success(followService.listBarSnapshots(UserContext.getUserId(), barIds));
    }

    /**
     * 是否已关注指定贴吧
     */
    @GetMapping("/{barId}")
    public Result<Boolean> isFollowed(@PathVariable("barId") Long barId) {
        return Result.success(followService.isFollowed(UserContext.getUserId(), barId));
    }

    /**
     * 取消关注指定贴吧
     */
    @DeleteMapping("/{barId}")
    public Result<String> unfollow(@PathVariable("barId") Long barId) {
        followService.unfollow(UserContext.getUserId(), barId);
        return Result.success("取消关注成功");
    }
}
