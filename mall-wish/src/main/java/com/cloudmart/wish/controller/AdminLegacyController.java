package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.ContentFlowLog;
import com.cloudmart.wish.entity.LeaderboardConfig;
import com.cloudmart.wish.service.LegacyFlowService;
import com.cloudmart.wish.service.LeaderboardConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理后台传承 + 排行榜 Controller（Sprint 2.7 管理后台）。
 *
 * <p>路由前缀 /admin/legacy 与 /admin/leaderboard，仅内部服务调用；
 * 权限点 {@code business:legacy:list/retry}、{@code business:leaderboard:list/edit}
 * 在管理后台角色界面配置。</p>
 */
@RestController
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "管理后台-传承与排行榜", description = "内容流转日志/传承统计/排行榜配置（Sprint 2.7）")
public class AdminLegacyController {

    private final LegacyFlowService legacyFlowService;
    private final LeaderboardConfigService leaderboardConfigService;

    @GetMapping("/admin/legacy/flows")
    @Operation(summary = "内容流转日志", description = "status 过滤可选（SUCCESS/FAILED/HIDDEN），id 倒序，默认 20 条")
    public ApiResponse<List<ContentFlowLog>> listFlowLogs(
            @Parameter(description = "状态过滤") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.ok(legacyFlowService.listFlowLogs(status, page, size));
    }

    @PostMapping("/admin/legacy/flows/{id}/retry")
    @Operation(summary = "重试流转", description = "对 FAILED 的流转单次重试（community 可用时成功并回填 postId）")
    public ApiResponse<Void> retryFlow(
            @Parameter(description = "流转日志 ID", required = true) @PathVariable Long id) {
        legacyFlowService.retryFlow(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/legacy/stats")
    @Operation(summary = "传承统计", description = "触达率以推送成功率计（sum(pushed)/sum(target)，"
            + "查看率需 mall-notification 阅读埋点，契约偏差已留档）")
    public ApiResponse<LegacyFlowService.LegacyStats> legacyStats() {
        return ApiResponse.ok(legacyFlowService.getLegacyStats());
    }

    @GetMapping("/admin/leaderboard/configs")
    @Operation(summary = "排行榜配置列表", description = "刷新周期/Top N/同分处理/封禁过滤")
    public ApiResponse<List<LeaderboardConfig>> leaderboardConfigs() {
        return ApiResponse.ok(leaderboardConfigService.listConfigs());
    }

    @PutMapping("/admin/leaderboard/configs/{key}")
    @Operation(summary = "更新排行榜配置", description = "校验通过即回填缓存实时生效（下次刷新任务按新值执行）")
    public ApiResponse<LeaderboardConfig> updateLeaderboardConfig(
            @Parameter(description = "配置键", required = true) @PathVariable("key") String configKey,
            @RequestBody ConfigUpdateRequest request,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(leaderboardConfigService.updateConfig(configKey, request.configValue(), adminUserId));
    }

    /** 配置更新请求体。 */
    public record ConfigUpdateRequest(@NotBlank(message = "配置值不能为空") String configValue) {
    }
}
