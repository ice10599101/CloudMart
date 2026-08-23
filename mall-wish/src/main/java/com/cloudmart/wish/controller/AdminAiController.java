package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.AiConfigUpdateRequest;
import com.cloudmart.wish.dto.AiPromptCreateRequest;
import com.cloudmart.wish.dto.AiPromptStatusUpdateRequest;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.service.AiConfigService;
import com.cloudmart.wish.service.AiPromptService;
import com.cloudmart.wish.vo.AiConfigVO;
import com.cloudmart.wish.vo.AiPromptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * 管理后台 AI 心愿助手 Controller（文档 2.5 管理后台，Sprint 2.5）。
 *
 * <p>路由前缀 /admin/ai，仅内部服务调用（mall-admin 经 Feign 代理转发，
 * hasRole('INTERNAL') 由 X-Internal-Call 头授予）；权限点
 * {@code business:aiPrompt:*} / {@code business:aiConfig:*} 在管理后台
 * 角色界面配置。管理员身份经 {@code X-User-Id} 头透传。</p>
 *
 * <p>Prompt 版本管理：正文不可变（修改须建新版本）；DRAFT→ACTIVE 生效、
 * ACTIVE→ARCHIVED 下线；同 scene 多条 ACTIVE 按 traffic_percent 加权分流
 * （A/B 测试）；运行时 60s 缓存，修改后最迟 1 分钟生效，不重部署。</p>
 */
@RestController
@RequestMapping("/admin/ai")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-AI 心愿助手", description = "Prompt 模板版本管理 + A/B 分流 + 提醒策略配置")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiPromptService aiPromptService;
    private final AiConfigService aiConfigService;

    // ---------------- Prompt 模板管理 ----------------

    @GetMapping("/prompts")
    @Operation(summary = "模板列表", description = "含 DRAFT/ACTIVE/ARCHIVED 全状态；"
            + "scene 过滤可选，scene+version 倒序")
    public ApiResponse<List<AiPromptVO>> listPrompts(
            @Parameter(description = "场景过滤：GOAL_BREAKDOWN/TREE_HOLE/ANNUAL_REPORT/EXPECTED_GUIDE")
            @RequestParam(required = false) AiPromptScene scene) {
        return ApiResponse.ok(aiPromptService.listPrompts(scene));
    }

    @PostMapping("/prompts")
    @Operation(summary = "创建新版本模板", description = "初始 DRAFT（不生效）；"
            + "version 在 scene 内自动递增；激活后进入 A/B 分流")
    public ApiResponse<AiPromptVO> createPrompt(
            @Valid @RequestBody AiPromptCreateRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(aiPromptService.createPrompt(request, adminUserId));
    }

    @PutMapping("/prompts/{id}/status")
    @Operation(summary = "模板状态流转", description = "DRAFT→ACTIVE 生效 / ACTIVE→ARCHIVED 下线；"
            + "激活时可携带 trafficPercent 配置 A/B 分流权重；正文不可改（建新版本）")
    public ApiResponse<AiPromptVO> updatePromptStatus(
            @Parameter(description = "模板 ID", required = true) @PathVariable("id") Long promptId,
            @Valid @RequestBody AiPromptStatusUpdateRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(aiPromptService.updatePromptStatus(promptId, request, adminUserId));
    }

    // ---------------- 提醒策略配置 ----------------

    @GetMapping("/configs")
    @Operation(summary = "策略配置列表", description = "提醒频次/免打扰时段/预期管理限频/"
            + "年度报告缓存时长等全局策略项")
    public ApiResponse<List<AiConfigVO>> listConfigs() {
        return ApiResponse.ok(aiConfigService.listConfigs());
    }

    @PutMapping("/configs/{key}")
    @Operation(summary = "更新策略配置", description = "更新后主动失效缓存，实时生效；"
            + "键不存在返回 400")
    public ApiResponse<AiConfigVO> updateConfig(
            @Parameter(description = "配置键（如 reminder.daily_limit）", required = true)
            @PathVariable("key") String configKey,
            @Valid @RequestBody AiConfigUpdateRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(aiConfigService.updateConfig(configKey, request, adminUserId));
    }
}
