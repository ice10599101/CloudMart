package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
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

    /** 内部状态查询（SEC-04：仅服务令牌可达）：供 wish 等服务聚合真实进度 */
    @GetMapping("/status")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('INTERNAL')")
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
                "blockReason", task.getBlockReason() == null ? "" : task.getBlockReason(),
                "executeAfter", task.getExecuteAfter() == null ? "" : task.getExecuteAfter(),
                "serviceProgress", task.getServiceProgress() == null ? "{}" : task.getServiceProgress(),
                "executedAt", task.getExecutedAt() == null ? "" : task.getExecutedAt(),
                // T06：分域步骤台账（脱敏——仅状态/次数/错误码，无 PII/堆栈）
                "steps", orchestrationService.stepsOf(task.getId())));
    }

    /**
     * T06 内部端点（SEC-04：仅服务令牌可达，mall-admin 运营代理调用）：
     * 失败步骤立即重试（清零退避），不跳过资金阻断——OPEN_ORDER_CHECK 未通过时
     * 重试只会再次被预检拦下。
     */
    @PostMapping("/internal/tasks/{taskId}/retry")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "重试失败步骤（运营）", description = "按步骤台账立即重试；不提供跳过资金检查能力")
    public ApiResponse<Void> retryFailedSteps(
            @Parameter(description = "注销任务ID", required = true) @org.springframework.web.bind.annotation.PathVariable Long taskId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) RetryRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "重试必须填写运营原因（审计）");
        }
        orchestrationService.retryFailedSteps(taskId, request.reason());
        return ApiResponse.ok(null);
    }

    public record RetryRequest(String reason) {
    }
}
