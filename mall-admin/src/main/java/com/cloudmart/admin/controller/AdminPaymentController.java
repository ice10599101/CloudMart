package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.PaymentAttemptDTO;
import com.cloudmart.admin.feign.PaymentFeignClient;
import com.cloudmart.admin.feign.PaymentReconciliationFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务管理-支付域：支付尝试台账与支付对账（OPS-01）。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：支付/对账")
public class AdminPaymentController {

    private final PaymentFeignClient paymentFeignClient;
    private final PaymentReconciliationFeignClient paymentReconciliationFeignClient;

    public AdminPaymentController(PaymentFeignClient paymentFeignClient,
                                  PaymentReconciliationFeignClient paymentReconciliationFeignClient) {
        this.paymentFeignClient = paymentFeignClient;
        this.paymentReconciliationFeignClient = paymentReconciliationFeignClient;
    }

    // ==================== 支付 ====================

    @GetMapping("/payments")
    @RequiresPermission("business:payment:list")
    @Operation(summary = "支付列表", description = "分页查询支付记录，支持按状态筛选")
    public ApiResponse<List<PaymentAttemptDTO>> listPayments(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return paymentFeignClient.listPayments(status, page, pageSize);
    }

    @GetMapping("/payments/order/{orderId}")
    @RequiresPermission("business:payment:list")
    @Operation(summary = "查询支付尝试", description = "根据订单ID查询最近一次支付尝试（T01：attempt 台账）")
    public ApiResponse<PaymentAttemptDTO> getPaymentByOrder(@PathVariable Long orderId) {
        return paymentFeignClient.getPaymentByOrderId(orderId);
    }
    // T01：旧 /payments/{paymentId}/refund 退款代理已删除——渠道退款事实由 T02 refund_order 契约重建

    // ==================== 支付对账（OPS-01） ====================
    // 说明：查询用 business:payment:list；执行/处置用细粒度 business:payment:reconcile
    // （权限种子见 mall-admin/src/main/resources/db/migration/V15__payment_reconcile_perms.sql，
    // 超管通配不受影响；自定义角色经菜单管理/角色管理授予）。人工处置不直接改资金。

    @GetMapping("/payments/reconciliation/runs")
    @RequiresPermission("business:payment:list")
    @Operation(summary = "对账运行列表", description = "按日期倒序；差异汇总随行；meta 携带分页")
    public ApiResponse<Object> listReconciliationRuns(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return paymentReconciliationFeignClient.listRuns(page, size);
    }

    @GetMapping("/payments/reconciliation/runs/{runId}/differences")
    @RequiresPermission("business:payment:list")
    @Operation(summary = "对账差异列表", description = "按运行查差异；resolveStatus 过滤（默认 OPEN）；分页返回")
    public ApiResponse<Object> listReconciliationDifferences(
            @PathVariable Long runId,
            @RequestParam(value = "resolveStatus", required = false) String resolveStatus,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return paymentReconciliationFeignClient.listDifferences(runId, resolveStatus, page, size);
    }

    @PostMapping("/payments/reconciliation/runs/execute")
    @OperLog(title = "支付对账执行", businessType = 1)
    @RequiresPermission("business:payment:reconcile")
    @Operation(summary = "执行一次对账", description = "T11：scope=PAYMENT_ORDER/REFUND/INVENTORY；scanDays 扫描最近 N 天")
    public ApiResponse<Object> executeReconciliationRun(
            @RequestParam(value = "scanDays", defaultValue = "7") int scanDays,
            @RequestParam(value = "scope", defaultValue = "PAYMENT_ORDER") String scope) {
        return paymentReconciliationFeignClient.executeRun(scanDays, scope);
    }

    @PostMapping("/payments/reconciliation/differences/{diffId}/resolve")
    @OperLog(title = "支付对账差异处置", businessType = 2)
    @RequiresPermission("business:payment:reconcile")
    @Operation(summary = "处置差异", description = "resolveStatus=RESOLVED/ACCEPTED + 处置说明；不直接改资金")
    public ApiResponse<Void> resolveReconciliationDifference(@PathVariable Long diffId,
                                                             @RequestBody Map<String, Object> body) {
        return paymentReconciliationFeignClient.resolveDifference(diffId, body);
    }
}
