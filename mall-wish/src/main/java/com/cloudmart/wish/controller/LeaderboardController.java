package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.LeaderboardService;
import com.cloudmart.wish.service.LeaderboardService.LeaderboardType;
import com.cloudmart.wish.vo.LeaderboardEntryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排行榜 Controller（Sprint 2.7，文档 2.9：GET /wish/leaderboard）。
 *
 * <p>公开浏览（permitAll）；数据源 Redis ZSet 命中（每 10 分钟由 mall-job
 * 刷新），P95 < 200ms；同分按 created_at 升序（早在前，配置可调）。</p>
 */
@RestController
@RequestMapping("/leaderboard")
@Tag(name = "排行榜", description = "热门/温暖/坚持/星火四榜单 Top 100（Sprint 2.7）")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    @Operation(summary = "排行榜查询", description = "type: HOT(点亮榜/心愿)/WARM(祝福榜/心愿)/"
            + "PERSISTENCE(坚持榜/打卡天数)/SPARK(星火榜/帮助次数)；rankDelta 为排名变化（UP/DOWN/FLAT/NEW，"
            + "三端动效一致依据）；limit 默认 50，最大 100")
    @SentinelResource("WISH_LEADERBOARD")
    public ApiResponse<List<LeaderboardEntryVO>> getLeaderboard(
            @Parameter(description = "榜单类型", required = true) @RequestParam String type,
            @Parameter(description = "条数（默认 50，最大 100）") @RequestParam(defaultValue = "50") Integer limit) {
        LeaderboardType board;
        try {
            board = LeaderboardType.valueOf(type.trim());
        } catch (IllegalArgumentException ex) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.wish.constant.WishErrorCodes.WISH_LEADERBOARD_TYPE_INVALID,
                    "榜单类型非法: " + type);
        }
        int safeLimit = Math.min(Math.max(limit == null ? 50 : limit, 1), 100);
        return ApiResponse.ok(leaderboardService.getBoard(board, safeLimit));
    }
}
