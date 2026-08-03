package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.community.service.CommunityStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/stats")
@Tag(name = "社区统计(后台)", description = "管理后台社区数据统计接口")
@RequiredArgsConstructor
public class CommunityStatsController {

    private final CommunityStatsService communityStatsService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "社区概览统计", description = "获取社区关键统计数据：今日新帖、待审核数、活跃用户、总帖子数、待处理举报等")
    public ApiResponse<Map<String, Object>> getOverviewStats() {
        Map<String, Object> stats = communityStatsService.getOverviewStats();
        return ApiResponse.ok(stats);
    }

    @GetMapping("/trend")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "社区趋势统计", description = "获取近N天的社区数据趋势：每日帖子数、评论数、举报数")
    public ApiResponse<List<Map<String, Object>>> getTrendStats(
            @Parameter(description = "天数") @RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> trend = communityStatsService.getTrendStats(days);
        return ApiResponse.ok(trend);
    }
}
