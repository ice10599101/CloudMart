package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.AdminEnvConfigRequest;
import com.cloudmart.wish.dto.TriggerSpecialEventRequest;
import com.cloudmart.wish.service.AdminTreeEnvService;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台生命树环境 Controller（文档 Sprint 2.2 管理后台：特殊事件
 * 触发台 + 环境配置管理 + 天气 API 配置说明）。
 *
 * <p>路由前缀 /admin/tree-env，仅内部服务调用（mall-admin 经 Feign 代理
 * 转发，hasRole('INTERNAL') 由 X-Internal-Call 头授予）；权限点
 * {@code business:treeEnv:*} 在管理后台角色界面配置。管理员身份经
 * {@code X-User-Id} 头透传（AdminFeignInterceptor）。</p>
 *
 * <p>天气 API 配置（API Key/选型/降级策略）：属部署配置经 Nacos
 * {@code wish.tree-env.weather.*} 下发（含 QWEATHER_API_KEY 环境变量），
 * 无需运行时接口修改；本 Controller 提供配置读取入口见
 * {@code AdminEnvConfigRequest} visual 编辑能力。</p>
 */
@RestController
@RequestMapping("/admin/tree-env")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-生命树环境", description = "特殊事件触发台 + 环境配置管理（表化，新增环境不改代码）")
@RequiredArgsConstructor
public class AdminTreeEnvController {

    private final AdminTreeEnvService adminTreeEnvService;

    // ---------------- 特殊事件触发台 ----------------

    @PostMapping("/special-events")
    @Operation(summary = "触发全站特殊事件", description = "如流星雨/极光/星辰夜，全站同步展示；"
            + "单活跃事件语义（自动结束当前活跃事件）。eventCode 须为已启用的环境配置 code；"
            + "durationMinutes 为空持续至手动结束")
    public ApiResponse<SpecialEventVO> triggerSpecialEvent(
            @Valid @RequestBody TriggerSpecialEventRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(adminTreeEnvService.triggerSpecialEvent(request, adminUserId));
    }

    @PutMapping("/special-events/{id}/end")
    @Operation(summary = "手动结束特殊事件", description = "幂等：已结束直接返回。"
            + "结束后全站恢复常规环境展示")
    public ApiResponse<SpecialEventVO> endSpecialEvent(
            @Parameter(description = "事件 ID", required = true) @PathVariable("id") Long eventId) {
        return ApiResponse.ok(adminTreeEnvService.endSpecialEvent(eventId));
    }

    @GetMapping("/special-events")
    @Operation(summary = "特殊事件历史列表", description = "triggeredAt 倒序（触发台历史记录）")
    public ApiResponse<List<SpecialEventVO>> listSpecialEvents(
            @Parameter(description = "返回条数上限（默认 50，最大 200）", example = "50")
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ApiResponse.ok(adminTreeEnvService.listSpecialEvents(limit));
    }

    // ---------------- 环境配置管理（表化） ----------------

    @GetMapping("/configs")
    @Operation(summary = "环境配置全量列表", description = "含下架配置（管理端需查看与操作全部），"
            + "priority 降序；visual 为渲染参数 JSON 对象供编辑器回显")
    public ApiResponse<List<EnvConfigVO>> listEnvConfigs() {
        return ApiResponse.ok(adminTreeEnvService.listEnvConfigs());
    }

    @PostMapping("/configs")
    @Operation(summary = "新增环境配置", description = "环境配置表化核心入口：新增\"中秋\"等"
            + "环境仅插入配置行，四端渲染不改代码。envCode 唯一；visual 必须为 JSON 对象")
    public ApiResponse<EnvConfigVO> createEnvConfig(@Valid @RequestBody AdminEnvConfigRequest request) {
        return ApiResponse.ok(adminTreeEnvService.createEnvConfig(request));
    }

    @PutMapping("/configs/{id}")
    @Operation(summary = "编辑环境配置", description = "envCode 不可修改（天气/季节/事件链路"
            + "关联键）；visual 结构校验同新增")
    public ApiResponse<EnvConfigVO> updateEnvConfig(
            @Parameter(description = "配置 ID", required = true) @PathVariable("id") Long configId,
            @Valid @RequestBody AdminEnvConfigRequest request) {
        return ApiResponse.ok(adminTreeEnvService.updateEnvConfig(configId, request));
    }

    @PutMapping("/configs/{id}/status")
    @Operation(summary = "上/下架环境配置", description = "下架后不出现在公开配置列表，"
            + "特殊事件触发校验失败（无启用配置）")
    public ApiResponse<EnvConfigVO> updateEnvConfigStatus(
            @Parameter(description = "配置 ID", required = true) @PathVariable("id") Long configId,
            @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        return ApiResponse.ok(adminTreeEnvService.updateEnvConfigStatus(configId,
                active == null || active));
    }
}
