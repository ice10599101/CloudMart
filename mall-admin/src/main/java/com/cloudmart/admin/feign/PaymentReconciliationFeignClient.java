package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * mall-payment 对账工作台 Feign 客户端（OPS-01）。
 *
 * <p>下游端点 {@code /admin/payments/reconciliation/**} 由
 * {@code @PreAuthorize("hasRole('INTERNAL')")} 保护；出站服务令牌由
 * {@code ServiceTokenFeignInterceptor} 按 outbound-scopes（mall-payment=payment:admin）
 * 自动签发。人工处置不直接改资金，只登记证据/处置说明。</p>
 */
@FeignClient(contextId = "paymentReconciliationFeignClient", name = "mall-payment",
        path = "/admin/payments/reconciliation",
        fallbackFactory = PaymentReconciliationFeignClientFallbackFactory.class)
public interface PaymentReconciliationFeignClient {

    @GetMapping("/runs")
    ApiResponse<Object> listRuns(@RequestParam("page") int page,
                                 @RequestParam("size") int size);

    @GetMapping("/runs/{runId}/differences")
    ApiResponse<Object> listDifferences(@PathVariable("runId") Long runId,
                                        @RequestParam(value = "resolveStatus", required = false) String resolveStatus,
                                        @RequestParam("page") int page,
                                        @RequestParam("size") int size);

    @PostMapping("/runs/execute")
    ApiResponse<Object> executeRun(@RequestParam("scanDays") int scanDays,
                                   @org.springframework.web.bind.annotation.RequestParam("scope")
                                   @org.springframework.lang.NonNull String scope);

    @PostMapping("/differences/{diffId}/resolve")
    ApiResponse<Void> resolveDifference(@PathVariable("diffId") Long diffId,
                                        @RequestBody Map<String, Object> body);
}
