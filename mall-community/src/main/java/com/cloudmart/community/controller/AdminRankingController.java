package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.community.service.AdminRankingService;
import com.cloudmart.community.service.RankingService;
import com.cloudmart.community.vo.RankingItemVO;
import com.cloudmart.community.vo.RankingSeasonVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 排行榜管理接口（后台）
 * <p>
 * 供运营后台调用，管理赛季、查看榜单、手动归档。所有接口限内部服务调用。
 */
@RestController
@RequestMapping("/admin/rankings")
@Tag(name = "排行榜管理(后台)", description = "赛季管理、榜单查看、手动归档接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminRankingController {

    private final RankingService rankingService;
    private final AdminRankingService adminRankingService;

    @GetMapping("/current")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "当月排行榜", description = "获取当月经验值排行榜 Top N，用于管理后台概览")
    public ApiResponse<List<RankingItemVO>> getCurrentRanking(
            @Parameter(description = "榜单大小") @RequestParam(defaultValue = "50") int size) {
        List<RankingItemVO> ranking = rankingService.getMonthlyRanking(size);
        return ApiResponse.ok(ranking);
    }

    @PostMapping("/persist")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "手动归档上赛季", description = "将上个月的 Redis ZSet 榜单数据持久化到 MySQL 并归档为赛季")
    public ApiResponse<Void> persistLastMonthRanking() {
        rankingService.persistLastMonthRanking();
        return ApiResponse.ok(null);
    }

    @GetMapping("/seasons")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "赛季列表", description = "分页查询赛季列表，支持按状态筛选")
    public ApiResponse<List<RankingSeasonVO>> listSeasons(
            @Parameter(description = "状态（0-进行中，1-已归档，不传则全部）") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<RankingSeasonVO> result = adminRankingService.listSeasons(page, size, status);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/seasons/{seasonId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "赛季榜单详情", description = "分页查询指定赛季的榜单记录")
    public ApiResponse<List<RankingItemVO>> getSeasonRanking(
            @Parameter(description = "赛季ID", required = true) @PathVariable Long seasonId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<RankingItemVO> result = rankingService.getSeasonRanking(seasonId, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PutMapping("/seasons/{seasonId}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "修改赛季状态", description = "修改赛季状态（0-进行中，1-已归档）")
    public ApiResponse<Void> updateSeasonStatus(
            @Parameter(description = "赛季ID", required = true) @PathVariable Long seasonId,
            @Parameter(description = "状态信息") @RequestBody Map<String, Integer> body) {
        adminRankingService.updateSeasonStatus(seasonId, body.get("status"));
        return ApiResponse.ok(null);
    }
}
