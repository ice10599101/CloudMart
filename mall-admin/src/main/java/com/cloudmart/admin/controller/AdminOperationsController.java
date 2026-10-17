package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.OperationsClients.OrderOperationsClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.web.bind.annotation.RequestBody;
import com.cloudmart.admin.feign.OperationsClients.WishOperationsClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Function;

/**
 * T16 异常处理中心（mall-admin 汇总）：按 service 参数路由到对应业务域的
 * 脱敏 outbox 视图；重试在各业务域执行（管理服务不直接写他库）。
 * 现已接入：mall-order / mall-wish（公共与心愿 Outbox 代表域）。
 *
 * <p>R15 契约：网关剥离 /api/admin 后本控制器前缀为 /operations，
 * 外部 URL 为 /api/admin/operations/**；Feign 返回的 ApiResponse 直接透传，
 * 不再二次包装（禁止 data.data 双层）；分页参数收 pageSize（兼容 size），
 * 下游统一 size；重试 reason 必填且随 @OperLog 审计留痕（含操作者）。</p>
 */
@RestController
@RequestMapping("/operations")
@Tag(name = "异常处理中心", description = "T16：跨域失败任务查询与受控重试（脱敏视图）")
@RequiredArgsConstructor
@Validated
public class AdminOperationsController {

    private final OrderOperationsClient orderOperationsClient;
    private final WishOperationsClient wishOperationsClient;
    private final com.cloudmart.admin.feign.OperationsClients.UserOperationsClient userOperationsClient;
    private final com.cloudmart.admin.feign.OperationsClients.ExportOperationsClient exportOperationsClient;

    @GetMapping("/outbox")
    @RequiresPermission("operations:read")
    @Operation(summary = "失败任务分页", description = "T16：service=mall-order/mall-wish；status 过滤；"
            + "视图不含 payload（脱敏）；records/total/stats 单层返回")
    public ApiResponse<Object> page(
            @RequestParam String service,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer size) {
        // pageSize 为标准入参；size 兼容旧调用方，二者都传时以 pageSize 为准
        int effectiveSize = pageSize != null ? pageSize : (size != null ? size : 20);
        return route(service).apply(new Object[]{status, page, effectiveSize});
    }

    @PostMapping("/outbox/{service}/{eventId}/retry")
    @OperLog(title = "异常处理中心", businessType = 2)
    @RequiresPermission("operations:retry")
    @Operation(summary = "重试死信", description = "T16：仅 DEAD/DEAD_LETTER 可重试（受理≠成功）；"
            + "原业务键不变，重复点击幂等；reason 必填并随审计留痕；返回受理结果与受理后实际状态")
    public ApiResponse<Object> retry(@PathVariable String service,
                                     @PathVariable String eventId,
                                     @Parameter(description = "重试原因（必填，≤500 字，随操作审计留痕）", required = true)
                                     @RequestParam @Size(max = 500, message = "重试原因不能超过 500 字") String reason) {
        return routeRetry(service).apply(eventId);
    }

    // ---- 内部路由：service → Feign 调用（避免管理服务直连各库） ----

    private Function<Object[], ApiResponse<Object>> route(String service) {
        return switch (service == null ? "" : service.toLowerCase()) {
            case "mall-order", "order" ->
                    (args) -> orderOperationsClient.listOutbox(
                            (String) args[0], (Integer) args[1], (Integer) args[2]);
            case "mall-wish", "wish" ->
                    (args) -> wishOperationsClient.listOutbox(
                            (String) args[0], (Integer) args[1], (Integer) args[2]);
            default -> throw new com.cloudmart.common.exception.BusinessException(
                    "OPERATIONS_SERVICE_UNSUPPORTED",
                    "异常处理中心未接入该域: " + service + "（现已接入 mall-order/mall-wish）");
        };
    }

    private Function<String, ApiResponse<Object>> routeRetry(String service) {
        return switch (service == null ? "" : service.toLowerCase()) {
            case "mall-order", "order" -> orderOperationsClient::retryOutbox;
            case "mall-wish", "wish" -> wishOperationsClient::retryOutbox;
            default -> throw new com.cloudmart.common.exception.BusinessException(
                    "OPERATIONS_SERVICE_UNSUPPORTED",
                    "异常处理中心未接入该域: " + service + "（现已接入 mall-order/mall-wish）");
        };
    }

    // ===== OPS-03：账号注销任务（mall-user）/ 心愿数据导出（mall-wish）运维处置 =====

    @GetMapping("/deletion")
    @RequiresPermission("operations:read")
    @Operation(summary = "注销任务分页", description = "OPS-03：任务台账事实（状态/服务进度/阻断原因）")
    public ApiResponse<Object> deletionTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return userOperationsClient.listTasks(status, page, pageSize);
    }

    @PostMapping("/deletion/{taskId}/retry")
    @OperLog(title = "异常处理中心-注销", businessType = 2)
    @RequiresPermission("operations:retry")
    @Operation(summary = "重试注销失败步骤", description = "OPS-03：按步骤台账立即重试（reason 必填随审计留痕）；不提供跳过资金检查能力")
    public ApiResponse<Object> retryDeletion(
            @PathVariable long taskId,
            @RequestBody java.util.Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new com.cloudmart.common.exception.BusinessException("VALIDATION_ERROR", "重试必须填写运营原因（审计）");
        }
        return userOperationsClient.retryTask(taskId, body);
    }

    @GetMapping("/exports")
    @RequiresPermission("operations:read")
    @Operation(summary = "心愿数据导出任务分页", description = "OPS-03：状态过滤；脱敏视图（不含密文/正文）")
    public ApiResponse<Object> exportTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return exportOperationsClient.listTasks(status, page, pageSize);
    }

    @PostMapping("/exports/{id}/retry")
    @OperLog(title = "异常处理中心-导出", businessType = 2)
    @RequiresPermission("operations:retry")
    @Operation(summary = "受控重试失败导出", description = "OPS-03：FAILED→PENDING 重排（reason 必填随审计留痕）；由持续 worker 认领执行，不跳过校验")
    public ApiResponse<Object> retryExport(
            @PathVariable long id,
            @RequestBody java.util.Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new com.cloudmart.common.exception.BusinessException("VALIDATION_ERROR", "重试必须填写运营原因（审计）");
        }
        return exportOperationsClient.retryTask(id, body);
    }
}
