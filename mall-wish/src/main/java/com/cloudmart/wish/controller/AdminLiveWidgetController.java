package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.LiveWidgetConfig;
import com.cloudmart.wish.service.LiveWidgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台直播挂件 Controller（Sprint 3.4 管理后台：挂件配置/降级开关）。
 *
 * <p>路由前缀 /admin/live/widget，仅内部服务调用；权限点
 * {@code business:liveWidget:list/edit}。全局降级开关走灰度配置
 * feature_key=wish_live_widget（比例 0=全局隐藏，实时生效）。</p>
 */
@RestController
@RequestMapping("/admin/live/widget")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-直播挂件", description = "挂件样式/位置/可见性配置 + 降级开关（Sprint 3.4）")
@RequiredArgsConstructor
public class AdminLiveWidgetController {

    private final LiveWidgetService liveWidgetService;

    @GetMapping("/configs")
    @Operation(summary = "挂件配置列表", description = "全量主播挂件配置")
    public ApiResponse<List<LiveWidgetConfig>> listConfigs() {
        return ApiResponse.ok(liveWidgetService.listConfigs());
    }

    @PutMapping("/{streamerId}")
    @Operation(summary = "保存主播挂件配置", description = "streamer 维度 upsert；"
            + "position 四选一；style 为 JSON；配置保存即失效缓存（10s 内生效）")
    public ApiResponse<LiveWidgetConfig> saveConfig(
            @Parameter(description = "主播用户 ID", required = true) @PathVariable Long streamerId,
            @RequestBody LiveWidgetConfig config,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        config.setStreamerId(streamerId);
        config.setUpdatedBy(adminUserId);
        return ApiResponse.ok(liveWidgetService.saveConfig(config));
    }

    @PutMapping("/{streamerId}/visible")
    @Operation(summary = "启停主播挂件", description = "is_visible=false → 该主播挂件隐藏（降级）")
    public ApiResponse<Void> toggleVisible(
            @Parameter(description = "主播用户 ID", required = true) @PathVariable Long streamerId,
            @RequestParam boolean visible) {
        liveWidgetService.toggleConfig(streamerId, visible);
        return ApiResponse.ok(null);
    }
}
