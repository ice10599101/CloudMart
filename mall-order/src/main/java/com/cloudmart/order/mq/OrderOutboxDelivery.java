package com.cloudmart.order.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import com.cloudmart.order.config.RocketMQConfig;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单事件 Outbox 投递适配器（ASYNC-01）：把订单状态类事件发往 order-events。
 * 消息体为事件信封（含 eventId/payload），消费方按 eventId 做 Inbox 幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxDelivery implements OutboxDelivery {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String destination(EventEnvelope envelope) {
        if ("ORDER_ZERO_PAY".equals(envelope.eventType())) {
            // T18：零元单审计留痕事件，无下游消费方，仅落 Outbox 事实，不投递 MQ
            return "audit-local:ORDER_ZERO_PAY";
        }
        return RocketMQConfig.ORDER_TOPIC + ":" + tagFor(envelope.eventType());
    }

    @Override
    public void deliver(EventEnvelope envelope) {
        if ("ORDER_ZERO_PAY".equals(envelope.eventType())) {
            log.info("[T18] 零元单审计事件本地完成（不投递 MQ） orderId={}", envelope.aggregateId());
            return;
        }
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
            throw new IllegalStateException("order outbox delivery failed: " + e.getMessage(), e);
        }
    }

    private String tagFor(String eventType) {
        return switch (eventType) {
            case "ORDER_STATUS_CHANGE" -> RocketMQConfig.ORDER_TAG_STATUS_CHANGE;
            case "ORDER_PAID" -> RocketMQConfig.ORDER_TAG_PAID;
            case "SECKILL_RESULT" -> RocketMQConfig.ORDER_TAG_SECKILL_RESULT;
            case "AFTER_SALE_INSPECT_PASSED" -> RocketMQConfig.TAG_AFTER_SALE;
            case "AFTER_SALE_REFUND_SUBMIT" -> RocketMQConfig.TAG_AFTER_SALE;
            default -> throw new IllegalArgumentException("未知的订单事件类型: " + eventType);
        };
    }
}
