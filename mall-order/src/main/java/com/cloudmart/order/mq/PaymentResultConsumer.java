package com.cloudmart.order.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 支付结果消费者（ASYNC-01）：信封事件 + Inbox 幂等消费。
 *
 * <p>形状判定（ASYNC-01 断点 2）：新格式为事件信封——orderId 在 {@code payload}
 * 内，事件类型在顶层 {@code eventType}（v2 名 PAYMENT_SUCCEEDED / 旧名
 * PAYMENT_SUCCESS 均接受）；旧格式 orderId/event 在顶层。此前消费端按旧形状
 * 直接读顶层 orderId，新消息在解析阶段即 NPE 失败。</p>
 *
 * <p>begin 的消费记录与 markOrderPaid 的业务变更同一事务——业务回滚则记录一并
 * 回滚，MQ 重投后重试；重复投递命中已处理记录直接跳过。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.PAYMENT_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_PAYMENT_RESULT,
        selectorExpression = RocketMQConfig.PAYMENT_TAG_RESULT
)
public class PaymentResultConsumer implements RocketMQListener<Map<String, Object>> {

    static final String CONSUMER = "order-payment-result";

    private final OrderService orderService;
    private final InboxService inboxService;

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        Long orderId = extractOrderId(message);
        String event = extractEventType(message);
        String eventId = (String) message.get("eventId");
        log.info("收到支付结果消息, orderId={}, event={}, eventId={}", orderId, event, eventId);

        if (!"PAYMENT_SUCCESS".equals(event) && !"PAYMENT_SUCCEEDED".equals(event)) {
            return;
        }
        // 兼容旧格式消息（无 eventId）：退化为直接处理，不做 Inbox 判重
        if (eventId == null || eventId.isBlank()) {
            log.warn("[ASYNC01] 旧格式支付事件（无 eventId），跳过幂等控制, orderId={}", orderId);
            orderService.markOrderPaid(orderId);
            return;
        }

        EventEnvelope envelope = envelopeOf(message);
        if (inboxService.beginConsume(CONSUMER, envelope) == InboxService.ConsumeDecision.SKIP) {
            log.info("[ASYNC01] 支付事件已消费（幂等跳过） eventId={} orderId={}", eventId, orderId);
            return;
        }
        try {
            orderService.markOrderPaid(orderId);
            inboxService.completeConsume(CONSUMER, envelope);
        } catch (Exception e) {
            inboxService.failConsume(CONSUMER, envelope, e.getMessage());
            throw e;
        }
    }

    /** 形状判定：新格式 orderId 在 payload 内；旧格式在顶层 */
    static Long extractOrderId(Map<String, Object> message) {
        Object payload = message.get("payload");
        if (payload instanceof Map<?, ?> payloadMap && payloadMap.get("orderId") instanceof Number n) {
            return n.longValue();
        }
        if (message.get("orderId") instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalArgumentException("[ASYNC01] 消息缺少 orderId（新旧形状均未命中）: " + message.keySet());
    }

    /** 事件类型：新格式顶层 eventType；旧格式顶层 event */
    static String extractEventType(Map<String, Object> message) {
        Object eventType = message.get("eventType");
        if (eventType instanceof String s && !s.isBlank()) {
            return s;
        }
        return (String) message.get("event");
    }

    static EventEnvelope envelopeOf(Map<String, Object> message) {
        return new EventEnvelope(
                (String) message.get("eventId"),
                (String) message.get("eventType"),
                message.get("schemaVersion") == null ? 1 : ((Number) message.get("schemaVersion")).intValue(),
                String.valueOf(message.get("aggregateId")),
                message.get("aggregateVersion") == null ? 0 : ((Number) message.get("aggregateVersion")).longValue(),
                message.get("occurredAt") == null ? 0 : ((Number) message.get("occurredAt")).longValue(),
                (String) message.get("requestId"),
                null);
    }
}
