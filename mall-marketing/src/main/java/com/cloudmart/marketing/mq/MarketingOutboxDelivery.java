package com.cloudmart.marketing.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import com.cloudmart.marketing.config.RocketMQConfig;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 营销事件 Outbox 投递适配器（ASYNC-01/T10）：把成团事件发往 marketing-events。
 * 消息体为事件信封（含 eventId/payload），消费方按稳定订单键 + eventId 幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingOutboxDelivery implements OutboxDelivery {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String destination(EventEnvelope envelope) {
        return RocketMQConfig.MARKETING_TOPIC + ":" + tagFor(envelope.eventType());
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
            throw new IllegalStateException("marketing outbox delivery failed: " + e.getMessage(), e);
        }
    }

    private String tagFor(String eventType) {
        return switch (eventType) {
            case "GROUP_SUCCESS" -> RocketMQConfig.MARKETING_TAG_GROUP_SUCCESS;
            default -> throw new IllegalArgumentException("未知的营销事件类型: " + eventType);
        };
    }
}
