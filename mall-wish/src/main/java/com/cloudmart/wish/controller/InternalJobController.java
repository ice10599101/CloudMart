package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.service.CapsuleService;
import com.cloudmart.wish.service.HomeService;
import com.cloudmart.wish.service.WishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 心愿宇宙定时任务内部接口（mall-job XXL-Job 触发，Sprint 1.7）。
 *
 * <p>安全：{@code hasRole('INTERNAL')}——仅网关/mall-job 携带
 * {@code X-Internal-Call: true} 的内部请求可达（见 InternalCallAuthenticationFilter），
 * 外部请求 403。</p>
 *
 * <p>对应 JobHandler（需在 XXL-Job 控制台登记，见进度文件四D）：</p>
 * <ul>
 *   <li>wishOverdueScanHandler → POST /internal/jobs/overdue-scan（每日 00:30）</li>
 *   <li>homeHotCacheRefreshHandler → POST /internal/jobs/hot-cache-refresh（每 10 分钟）</li>
 *   <li>badgeCompensationScanHandler → POST /internal/jobs/badge-compensation-scan（每日 03:00）</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/jobs")
@Tag(name = "心愿宇宙·定时任务内部", description = "mall-job XXL-Job 定时任务专用（外部不可达）")
@RequiredArgsConstructor
public class InternalJobController {

    private final WishService wishService;
    private final HomeService homeService;
    private final BadgeService badgeService;
    private final CapsuleService capsuleService;

    @PostMapping("/overdue-scan")
    @Operation(summary = "OVERDUE 状态机扫描", description = "流转 expected_at 过期的 ACTIVE 心愿为 OVERDUE"
            + "（文档 1.2：每日 00:30）。幂等：重复扫描自动跳过已流转记录")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Integer>> overdueScan() {
        return ApiResponse.ok(Map.of("transferred", wishService.scanOverdueWishes()));
    }

    @PostMapping("/hot-cache-refresh")
    @Operation(summary = "热门推荐缓存刷新", description = "DEL wish:hot:feed 强制下次请求回源最新候选集"
            + "（每 10 分钟，与缓存 TTL 对齐）。Fail-Open：Redis 异常不阻断")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> hotCacheRefresh() {
        homeService.refreshHotCache();
        return ApiResponse.ok(null);
    }

    @PostMapping("/badge-compensation-scan")
    @Operation(summary = "徽章漏发补偿扫描", description = "游标分批遍历用户统计，逐用户重判定徽章"
            + "（每日 03:00 低峰）。补偿 MQ 消费失败进 DLQ 导致的漏发；幂等")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<BadgeService.CompensationResult> badgeCompensationScan() {
        return ApiResponse.ok(badgeService.compensationScan());
    }

    @PostMapping("/capsule-open-scan")
    @Operation(summary = "时间胶囊到期扫描", description = "分批 500 条 CAS 流转 SEALED→AVAILABLE"
            + "（open_at ≤ NOW()，UTC 判定）并对流转成功者推送通知（每 10 分钟，文档 9.2）。"
            + "幂等：重复扫描不重复推送")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<CapsuleService.ScanResult> capsuleOpenScan() {
        return ApiResponse.ok(capsuleService.scanAvailableCapsules());
    }
}
