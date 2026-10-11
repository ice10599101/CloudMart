package com.cloudmart.order.compensation;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.order.feign.RefundFeignClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * R10：迟到支付退款补偿处理器——订单已取消后渠道扣款才成功的资金处置。
 * 载荷：{"orderId":x,"userId":y,"paidAmount":"n","currency":"CNY","merchantPaymentNo":"..."}。
 *
 * <p>幂等：退款号固定为 LATE{orderId}，payment 侧按 refundNo 重放返回原单；
 * 同一订单的处置义务本身也由任务表 insertIfAbsent 去重（见 OrderServiceImpl
 * handleLatePaymentSucceeded）。金额取任务载荷中的实付事实，不按订单当前状态重算。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LatePaymentRefundCompensationHandler implements CompensationHandler {

    /** 退款号前缀：与售后案件 RFC{caseId} 区分，稳定可重放 */
    static final String REFUND_NO_PREFIX = "LATE";

    private final RefundFeignClient refundFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return "late-payment-refund";
    }

    @Override
    public void execute(String aggregateId, String payload) {
        JsonNode node = objectMapper.readTree(payload);
        long orderId = node.get("orderId").asLong();
        String refundNo = REFUND_NO_PREFIX + orderId;
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("refundNo", refundNo);
        request.put("orderId", orderId);
        // 订单侧不持有 attemptId：由支付服务按订单权威解析最近一笔成功支付
        request.put("paymentAttemptId", null);
        request.put("amount", new BigDecimal(node.get("paidAmount").asText()));
        request.put("currency", node.get("currency").asText());
        request.put("reasonCode", "LATE_PAYMENT");
        var resp = refundFeignClient.createRefund(request);
        if (resp == null || !resp.success()) {
            throw new IllegalStateException("late-payment refund rejected for order " + aggregateId
                    + ": " + (resp == null ? "null response" : resp.error()));
        }
        log.info("[R10] 迟到支付退款已提交 refundNo={} orderId={} status={}",
                refundNo, orderId, resp.data() == null ? "UNKNOWN" : resp.data().get("status"));
    }
}
