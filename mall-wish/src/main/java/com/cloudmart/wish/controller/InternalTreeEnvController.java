package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.service.TreeEnvService;
import com.cloudmart.wish.vo.TreeEnvVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 生命树环境内部接口（mall-job 定时调用，Sprint 2.2）。
 *
 * <p>安全：{@code hasRole('INTERNAL')}——仅网关/mall-job 携带
 * {@code X-Internal-Call: true} 的内部请求可达（见 InternalCallAuthenticationFilter），
 * 外部请求 403。</p>
 *
 * <p>对应 JobHandler（需在 XXL-Job 控制台登记）：</p>
 * <ul>
 *   <li>treeMoodScanHandler → POST /internal/tree-env/scan（每 5 分钟，Sprint 1.5）</li>
 *   <li>seasonScanHandler → POST /internal/tree-env/season-scan（每日 00:00，
 *       Sprint 2.2 季节落库：3-5 月春/6-8 月夏/9-11 月秋/12-2 月冬）</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/tree-env")
@Tag(name = "生命树环境·内部", description = "mall-job 定时任务专用（外部不可达）")
@RequiredArgsConstructor
public class InternalTreeEnvController {

    private final TreeEnvService treeEnvService;

    @PostMapping("/scan")
    @Operation(summary = "情绪扫描", description = "执行一次情绪聚合与状态机流转；"
            + "由 mall-job XXL-Job 每 5 分钟触发（JobHandler: treeMoodScanHandler）。"
            + "幂等：多实例并发由 Redis 锁互斥")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<TreeEnvVO> scan() {
        return ApiResponse.ok(treeEnvService.scan());
    }

    @PostMapping("/season-scan")
    @Operation(summary = "季节落库扫描", description = "按 UTC 日期判定季节写入"
            + "wish_world_tree_state.season（文档 Sprint 2.2：每日 00:00；"
            + "3-5 月春/6-8 月夏/9-11 月秋/12-2 月冬）。幂等：季节未变化不产生写")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Map<String, String>> seasonScan() {
        TreeSeason season = treeEnvService.scanSeason();
        return ApiResponse.ok(Map.of("season", season.name()));
    }
}
