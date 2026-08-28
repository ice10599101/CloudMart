package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.MatchConfig;
import com.cloudmart.wish.service.AdminMatchService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台同愿匹配 Controller（Sprint 2.6 管理后台：小组管理 + 算法配置）。
 *
 * <p>路由前缀 /admin/match，仅内部服务调用（mall-admin 经 Feign 代理转发，
 * hasRole('INTERNAL') 由 X-Internal-Call 头授予）；权限点
 * {@code business:matchGroup:list/close} / {@code business:matchConfig:list/edit}
 * 在管理后台角色界面配置。管理员身份经 X-User-Id 头透传。</p>
 */
@RestController
@RequestMapping("/admin/match")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-同愿匹配", description = "小组管理（查看/解散/活跃度监控）+ 匹配算法配置")
@RequiredArgsConstructor
public class AdminMatchController {

    private final AdminMatchService adminMatchService;

    @GetMapping("/groups")
    @Operation(summary = "小组列表", description = "全量小组（含 CLOSED）；status/keyword 过滤可选；"
            + "含组长昵称与最近活跃时间（活跃度监控口径）")
    public ApiResponse<List<AdminMatchService.AdminMatchGroupRow>> listGroups(
            @Parameter(description = "状态过滤：OPEN/FULL/CLOSED") @RequestParam(required = false) String status,
            @Parameter(description = "关键词模糊过滤") @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminMatchService.listGroups(status, keyword));
    }

    @PostMapping("/groups/{id}/dissolution")
    @Operation(summary = "强制解散", description = "异常小组处置：关闭小组 + 成员关系置 LEFT + 逐成员通知")
    public ApiResponse<Void> forceDissolve(
            @Parameter(description = "小组 ID", required = true) @PathVariable Long id,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        adminMatchService.forceDissolve(id, adminUserId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/configs")
    @Operation(summary = "匹配算法配置列表", description = "权重/相似度阈值/提醒与建组限频等配置项")
    public ApiResponse<List<MatchConfig>> listConfigs() {
        return ApiResponse.ok(adminMatchService.listConfigs());
    }

    @PutMapping("/configs/{key}")
    @Operation(summary = "更新匹配算法配置", description = "权重键 0-1、其余非负整数；更新即失效缓存实时生效；"
            + "键不存在返回 400")
    public ApiResponse<MatchConfig> updateConfig(
            @Parameter(description = "配置键（如 match.weight_keyword）", required = true)
            @PathVariable("key") String configKey,
            @RequestBody ConfigUpdateRequest request,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(adminMatchService.updateConfig(configKey, request.configValue(), adminUserId));
    }

    /** 配置更新请求体。 */
    public record ConfigUpdateRequest(@NotBlank(message = "配置值不能为空") String configValue) {
    }
}
