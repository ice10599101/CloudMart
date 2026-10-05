package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.OperationsClients.OrderOperationsClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.admin.feign.OperationsClients.WishOperationsClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T16 异常处理中心（mall-admin 汇总）：按 service 参数路由到对应业务域的
 * 脱敏 outbox 视图；重试在各业务域执行（管理服务不直接写他库）。
 * 现已接入：mall-order / mall-wish（公共与心愿 Outbox 代表域）；pet 域走
 * /admin/business/pet/operations（交易操作台账）。
 */
@RestController
@RequestMapping("/admin/operations")
@Tag(name = "异常处理中心", description = "T16：跨域失败任务查询与受控重试（脱敏视图）")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final OrderOperationsClient orderOperationsClient;
    private final WishOperationsClient wishOperationsClient;

    @GetMapping("/outbox")
    @RequiresPermission("operations:read")
    @Operation(summary = "失败任务分页", description = "T16：service=mall-order/mall-wish；status 过滤；"
            + "视图不含 payload（脱敏）；附各状态计数")
    public ApiResponse<Object> page(
            @RequestParam String service,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(route(service).apply(new Object[]{status, page, size}));
    }

    @PostMapping("/outbox/{service}/{eventId}/retry")
    @OperLog(title = "异常处理中心", businessType = 2)
    @RequiresPermission("operations:retry")
    @Operation(summary = "重试死信", description = "T16：仅 DEAD/DEAD_LETTER 可重试（受理≠成功）；"
            + "原业务键不变，重复点击幂等；reason 随审计留痕")
    public ApiResponse<Object> retry(@PathVariable String service,
                                     @PathVariable String eventId,
                                     @RequestParam(required = false) String reason) {
        return ApiResponse.ok(routeRetry(service).apply(eventId));
    }

    // ---- 内部路由：service → Feign 调用（避免管理服务直连各库） ----

    private java.util.function.Function<Object[], Object> route(String service) {
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

    private java.util.function.Function<String, Object> routeRetry(String service) {
        return switch (service == null ? "" : service.toLowerCase()) {
            case "mall-order", "order" -> (eventId) -> orderOperationsClient.retryOutbox(eventId);
            case "mall-wish", "wish" -> (eventId) -> wishOperationsClient.retryOutbox(eventId);
            default -> throw new com.cloudmart.common.exception.BusinessException(
                    "OPERATIONS_SERVICE_UNSUPPORTED",
                    "异常处理中心未接入该域: " + service + "（现已接入 mall-order/mall-wish）");
        };
    }
}
