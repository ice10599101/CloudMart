package com.cloudmart.payment.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.entity.RefundOrder;
import com.cloudmart.payment.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部退款接口（T02，方案 12.4）：仅订单服务（order:refund scope 服务令牌）可达，
 * 终端不得直接退款。refundNo 幂等：同号同参重放返回原结果，同号异参 409。
 * 渠道退款事实以本接口返回状态为准（MOCK 同步 SUCCEEDED；真实渠道 PROCESSING/UNKNOWN）。
 */
@RestController
@RequestMapping("/internal/refunds")
@RequiredArgsConstructor
@Validated
@Tag(name = "内部退款", description = "T02：订单服务专用退款接口（服务令牌 order:refund）")
public class InternalRefundController {

    private final RefundService refundService;

    public record CreateRefundRequest(
            @NotBlank String refundNo,
            @NotNull Long orderId,
            /** 可空：订单侧不持有 attemptId 时由支付服务按订单权威解析最近一笔成功支付 */
            Long paymentAttemptId,
            @NotNull BigDecimal amount,
            @NotBlank String currency,
            String reasonCode) {
    }

    @PostMapping
    @Operation(summary = "创建并提交退款", description = "refundNo 幂等；锁原支付行核算累计退款禁止超退；"
            + "MOCK 渠道同步完成同构确认（SUCCEEDED）；真实渠道未接入明确失败")
    public ApiResponse<Map<String, Object>> createRefund(@Valid
                                                         @RequestBody CreateRefundRequest request) {
        RefundOrder refund = refundService.createAndSubmit(request.refundNo(), request.orderId(),
                request.paymentAttemptId(), request.amount(), request.currency(), request.reasonCode());
        return ApiResponse.ok(toView(refund));
    }

    @GetMapping("/{refundNo}")
    @Operation(summary = "按退款号查询渠道处理状态", description = "UNKNOWN/PROCESSING 由查单任务收敛，"
            + "不得重新生成退款号")
    public ApiResponse<Map<String, Object>> getRefund(
            @Parameter(description = "商户退款号", required = true) @PathVariable String refundNo) {
        RefundOrder refund = refundService.findByRefundNo(refundNo);
        if (refund == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(toView(refund));
    }

    private Map<String, Object> toView(RefundOrder refund) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("refundNo", refund.getRefundNo());
        view.put("orderId", refund.getOrderId());
        view.put("paymentAttemptId", refund.getPaymentAttemptId());
        view.put("amount", refund.getRefundAmount());
        view.put("currency", refund.getCurrency());
        view.put("status", refund.getStatus());
        view.put("providerRefundNo", refund.getProviderRefundNo());
        view.put("errorCode", refund.getErrorCode());
        return view;
    }
}
