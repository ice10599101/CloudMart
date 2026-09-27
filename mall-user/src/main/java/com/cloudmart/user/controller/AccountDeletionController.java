package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.user.entity.AccountDeletionTask;
import com.cloudmart.user.service.AccountDeletionOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 全账号注销入口（B20）：统一由 mall-user 编排。
 * wish 端旧入口 /wish/my/account-deletion 兼容保留并返回真实进度。
 */
@RestController
@RequestMapping("/users/account-deletion")
@Tag(name = "全账号注销（B20 编排）")
@RequiredArgsConstructor
public class AccountDeletionController {

    private final AccountDeletionOrchestrationService orchestrationService;

    public record ApplyRequest(String reason) {
    }

    @PostMapping
    @Operation(summary = "申请全账号注销", description = "30 天宽限期；到期由 mall-user 统一编排各服务数据擦除")
    public ApiResponse<AccountDeletionTask> apply(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody(required = false) ApplyRequest request) {
        return ApiResponse.ok(orchestrationService.apply(userId, request == null ? null : request.reason()));
    }

    @DeleteMapping
    @Operation(summary = "取消注销", description = "CAS PENDING 且未过截止时间")
    public ApiResponse<AccountDeletionTask> cancel(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(orchestrationService.cancel(userId));
    }

    /** 内部状态查询（服务间调用，X-Internal-Call 认证）：供 wish 等服务聚合真实进度 */
    @GetMapping("/status")
    @Operation(summary = "按 userId 查询注销进度（内部）")
    public ApiResponse<Map<String, Object>> statusInternal(
            @RequestParam("userId") Long userId) {
        AccountDeletionTask task = orchestrationService.getByUser(userId);
        if (task == null) {
            return ApiResponse.ok(Map.of("status", "NONE"));
        }
        return ApiResponse.ok(Map.of(
                "status", task.getStatus() == null ? "NONE" : task.getStatus(),
                "executeAfter", task.getExecuteAfter() == null ? "" : task.getExecuteAfter(),
                "serviceProgress", task.getServiceProgress() == null ? "{}" : task.getServiceProgress()));
    }

    @GetMapping
    @Operation(summary = "注销进度", description = "任务状态与各服务清理进度")
    public ApiResponse<Map<String, Object>> status(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        AccountDeletionTask task = orchestrationService.getByUser(userId);
        if (task == null) {
            return ApiResponse.ok(Map.of("status", "NONE"));
        }
        return ApiResponse.ok(Map.of(
                "status", task.getStatus() == null ? "NONE" : task.getStatus(),
                "executeAfter", task.getExecuteAfter() == null ? "" : task.getExecuteAfter(),
                "serviceProgress", task.getServiceProgress() == null ? "{}" : task.getServiceProgress(),
                "executedAt", task.getExecutedAt() == null ? "" : task.getExecutedAt()));
    }
}
