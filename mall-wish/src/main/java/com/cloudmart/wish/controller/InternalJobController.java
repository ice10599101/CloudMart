package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.service.CapsuleService;
import com.cloudmart.wish.service.CompanionReminderService;
import com.cloudmart.wish.service.ExpectedManagementService;
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
    private final com.cloudmart.wish.service.LeaderboardService leaderboardService;

    private final WishService wishService;
    private final HomeService homeService;
    private final BadgeService badgeService;
    private final CapsuleService capsuleService;
    private final ExpectedManagementService expectedManagementService;
    private final CompanionReminderService companionReminderService;

    @PostMapping("/overdue-scan")
    @Operation(summary = "OVERDUE 状态机扫描 + 预期管理通知", description = "流转 expected_at 过期的 "
            + "ACTIVE 心愿为 OVERDUE（文档 1.2：每日 00:30），随后对刚到期心愿下发 AI 引导通知"
            + "（Sprint 2.5 预期管理：3 选项 + 每日限 3 条）。幂等：重复扫描自动跳过已流转记录，"
            + "通知失败不回滚流转")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, Integer>> overdueScan() {
        WishService.OverdueScanResult scanResult = wishService.scanOverdueWishesDetailed();
        ExpectedManagementService.NotifyResult notifyResult =
                expectedManagementService.notifyExpiredWishes(scanResult.wishes());
        return ApiResponse.ok(Map.of(
                "transferred", scanResult.transferred(),
                "notified", notifyResult.notified(),
                "skippedByLimit", notifyResult.skippedByLimit(),
                "skippedByPreference", notifyResult.skippedByPreference()));
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

    @PostMapping("/ai-reminder-scan")
    @Operation(summary = "AI 陪伴提醒扫描", description = "每小时触发；命中「用户本地时区 09 点」的"
            + "活跃用户（有 ACTIVE 心愿或 IN_PROGRESS 目标）推送陪伴提醒（AI_REMINDER）。"
            + "过滤：免打扰时段（wish_ai_config 可配）+ 每日 1 条 + 通知偏好。"
            + "幂等：Redis 日计数保证同一用户当日仅 1 条")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<CompanionReminderService.RemindResult> aiReminderScan() {
        return ApiResponse.ok(companionReminderService.scanAndRemind());
    }

    /** 排行榜刷新（Sprint 2.7，建议 Cron 0 0/10 * * * ?，幂等可安全重试） */
    @PostMapping("/leaderboard-refresh")
    public void leaderboardRefresh() {
        leaderboardService.refreshAll();
    }
}
