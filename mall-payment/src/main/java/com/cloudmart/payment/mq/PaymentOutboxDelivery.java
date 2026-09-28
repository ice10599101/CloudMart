package com.cloudmart.payment.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import com.cloudmart.payment.config.RocketMQConfig;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付事件 Outbox 投递适配器（ASYNC-01）：把 Outbox 事件发往 payment-events。
 *
 * <p>消息体为事件信封 JSON（eventId/eventType/aggregateId/... + payload 载荷对象），
 * 消费方按 eventId 做 Inbox 幂等。destination 按事件类型映射 tag。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxDelivery implements OutboxDelivery {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String destination(EventEnvelope envelope) {
        return RocketMQConfig.PAYMENT_TOPIC + ":" + tagFor(envelope.eventType());
    }

    @Override
    public void deliver(EventEnvelope envelope) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("eventId", envelope.eventId());
            body.put("eventType", envelope.eventType());
            body.put("schemaVersion", envelope.schemaVersion());
            body.put("aggregateId", envelope.aggregateId());
            body.put("aggregateVersion", envelope.aggregateVersion());
            body.put("occurredAt", envelope.occurredAt());
            body.put("requestId", envelope.requestId());
            body.put("payload", objectMapper.readValue(envelope.payload(), Map.class));

            rocketMQTemplate.syncSend(destination(envelope), MessageBuilder.withPayload(body).build());
        } catch (Exception e) {
            // 任何异常都视为投递失败，交给 Outbox 退避重试
            throw new IllegalStateException("payment outbox delivery failed: " + e.getMessage(), e);
        }
    }

    private String tagFor(String eventType) {
        return switch (eventType) {
            case "PAYMENT_SUCCESS" -> RocketMQConfig.PAYMENT_TAG_RESULT;
            case "PAYMENT_REFUND" -> RocketMQConfig.PAYMENT_TAG_REFUND;
            default -> throw new IllegalArgumentException("未知的支付事件类型: " + eventType);
        };
    }
}
