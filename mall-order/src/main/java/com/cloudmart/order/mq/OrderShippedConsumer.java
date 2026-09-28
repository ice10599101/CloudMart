package com.cloudmart.order.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.repository.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 订单发货消费者（WMS-01）：消费 wms 的 ORDER_SHIPPED（order-events:shipped），
 * CAS 推进订单 PAID → SHIPPED——订单发货以「真实包裹出库」为唯一事实来源，
 * 替代管理端无包裹记录的直接发货路径。
 *
 * <p>Inbox 幂等 + 形状判定（payload 取 orderId，旧格式顶层兼容），失败重抛给 MQ 重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_SHIPPED,
        selectorExpression = RocketMQConfig.ORDER_TAG_SHIPPED
)
public class OrderShippedConsumer implements RocketMQListener<Map<String, Object>> {

    static final String CONSUMER = "order-shipped";

    private final OrderMapper orderMapper;
    private final InboxService inboxService;

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        Long orderId = extractOrderId(message);
        String eventId = (String) message.get("eventId");

        if (eventId != null && !eventId.isBlank()) {
            EventEnvelope envelope = new EventEnvelope(
                    eventId,
                    (String) message.get("eventType"),
                    message.get("schemaVersion") == null ? 1 : ((Number) message.get("schemaVersion")).intValue(),
                    String.valueOf(message.get("aggregateId")),
                    message.get("aggregateVersion") == null ? 0 : ((Number) message.get("aggregateVersion")).longValue(),
                    message.get("occurredAt") == null ? 0 : ((Number) message.get("occurredAt")).longValue(),
                    (String) message.get("requestId"),
                    null);
            if (inboxService.beginConsume(CONSUMER, envelope) == InboxService.ConsumeDecision.SKIP) {
                log.info("[WMS01] 发货事件已消费（幂等跳过） eventId={} orderId={}", eventId, orderId);
                return;
            }
            try {
                advanceToShipped(orderId);
                inboxService.completeConsume(CONSUMER, envelope);
            } catch (Exception e) {
                inboxService.failConsume(CONSUMER, envelope, e.getMessage());
                throw e;
            }
        } else {
            advanceToShipped(orderId);
        }
    }

    /** CAS PAID → SHIPPED：退款/取消并发竞争下只有一个方向成功 */
    private void advanceToShipped(Long orderId) {
        int updated = orderMapper.updateStatusIfMatch(orderId, "PAID", "SHIPPED");
        if (updated == 0) {
            // 已 SHIPPED（重放）幂等成功；CANCELLED/REFUNDING 等终态不推进
            log.info("[WMS01] 订单不处于 PAID，跳过发货推进 orderId={}", orderId);
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
        throw new IllegalArgumentException("消息缺少 orderId（新旧形状均未命中）: " + message.keySet());
    }
}
