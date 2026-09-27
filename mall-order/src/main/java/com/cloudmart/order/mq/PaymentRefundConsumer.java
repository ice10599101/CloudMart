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
 * 退款消费者（ASYNC-01）：信封事件 + Inbox 幂等消费，业务为订单取消回调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.PAYMENT_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_PAYMENT_REFUND,
        selectorExpression = RocketMQConfig.PAYMENT_TAG_REFUND
)
public class PaymentRefundConsumer implements RocketMQListener<Map<String, Object>> {

    static final String CONSUMER = "order-payment-refund";

    private final OrderService orderService;
    private final InboxService inboxService;

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        Long orderId = ((Number) message.get("orderId")).longValue();
        String event = (String) message.get("event");
        log.info("收到退款消息, orderId={}, event={}, eventId={}", orderId, event, eventId);

        if (!"PAYMENT_REFUND".equals(event)) {
            return;
        }
        if (eventId == null || eventId.isBlank()) {
            log.warn("[ASYNC01] 旧格式退款事件（无 eventId），跳过幂等控制, orderId={}", orderId);
            orderService.notifyOrderCancel(orderId);
            return;
        }

        EventEnvelope envelope = PaymentResultConsumer.envelopeOf(message);
        if (inboxService.beginConsume(CONSUMER, envelope) == InboxService.ConsumeDecision.SKIP) {
            log.info("[ASYNC01] 退款事件已消费（幂等跳过） eventId={} orderId={}", eventId, orderId);
            return;
        }
        try {
            orderService.notifyOrderCancel(orderId);
            inboxService.completeConsume(CONSUMER, envelope);
        } catch (Exception e) {
            inboxService.failConsume(CONSUMER, envelope, e.getMessage());
            throw e;
        }
    }
}
