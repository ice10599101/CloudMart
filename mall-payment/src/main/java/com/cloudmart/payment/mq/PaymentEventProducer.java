package com.cloudmart.payment.mq;

import com.cloudmart.payment.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public boolean sendPaymentSuccess(Long orderId, Long paymentId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", orderId);
            payload.put("paymentId", paymentId);
            payload.put("event", "PAYMENT_SUCCESS");
            String destination = RocketMQConfig.PAYMENT_TOPIC + ":" + RocketMQConfig.PAYMENT_TAG_RESULT;
            rocketMQTemplate.syncSend(destination, payload);
            log.info("支付成功消息发送成功, orderId={}, paymentId={}", orderId, paymentId);
            return true;
        } catch (Exception e) {
            log.error("支付成功消息发送失败, orderId={}, paymentId={}: {}", orderId, paymentId, e.getMessage());
            return false;
        }
    }

    public boolean sendPaymentRefund(Long orderId, Long paymentId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", orderId);
            payload.put("paymentId", paymentId);
            payload.put("event", "PAYMENT_REFUND");
            String destination = RocketMQConfig.PAYMENT_TOPIC + ":" + RocketMQConfig.PAYMENT_TAG_REFUND;
            rocketMQTemplate.syncSend(destination, payload);
            log.info("退款消息发送成功, orderId={}, paymentId={}", orderId, paymentId);
            return true;
        } catch (Exception e) {
            log.error("退款消息发送失败, orderId={}, paymentId={}: {}", orderId, paymentId, e.getMessage());
            return false;
        }
    }
}
