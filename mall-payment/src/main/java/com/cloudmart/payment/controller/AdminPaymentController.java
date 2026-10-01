package com.cloudmart.payment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台支付查询（T01）：唯一真值源为 payment_attempt 台账——
 * 详情含 merchantPaymentNo、channel、providerTxnNo、expiresAt、状态。
 * 退款进度属 T02 refund_order 契约，不在本控制器。
 */
@RestController
@RequestMapping("/admin/payments")
@Tag(name = "支付管理(后台)", description = "管理后台支付尝试查询接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentAttemptMapper attemptMapper;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "支付尝试列表", description = "管理后台分页查询支付尝试，支持按状态筛选")
    public ApiResponse<List<Map<String, Object>>> listPayments(
            @Parameter(description = "支付状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Page<PaymentAttempt> result = attemptMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<PaymentAttempt>()
                        .eq(status != null && !status.isBlank(), PaymentAttempt::getStatus, status)
                        .orderByDesc(PaymentAttempt::getId));
        List<Map<String, Object>> records = result.getRecords().stream().map(this::toView).toList();
        return ApiResponse.ok(records, new Meta(page, pageSize, result.getTotal()));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "按订单查询支付尝试", description = "返回该订单最近一次支付尝试详情")
    public ApiResponse<Map<String, Object>> getPaymentByOrderId(
            @Parameter(description = "订单ID") @PathVariable("orderId") Long orderId) {
        PaymentAttempt attempt = attemptMapper.selectOne(new LambdaQueryWrapper<PaymentAttempt>()
                .eq(PaymentAttempt::getOrderId, orderId)
                .orderByDesc(PaymentAttempt::getId)
                .last("LIMIT 1"));
        if (attempt == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(toView(attempt));
    }

    /** 后台尝试视图（T01：渠道事实全量可见，含渠道单号与有效期） */
    private Map<String, Object> toView(PaymentAttempt attempt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("attemptId", attempt.getId());
        view.put("orderId", attempt.getOrderId());
        view.put("merchantPaymentNo", attempt.getMerchantPaymentNo());
        view.put("channel", attempt.getChannel());
        view.put("status", attempt.getStatus());
        view.put("amount", attempt.getAmount());
        view.put("currency", attempt.getCurrency());
        view.put("providerTxnNo", attempt.getProviderTxnNo());
        view.put("expiresAt", attempt.getExpiresAt() == null ? null : attempt.getExpiresAt().toString());
        return view;
    }
}
