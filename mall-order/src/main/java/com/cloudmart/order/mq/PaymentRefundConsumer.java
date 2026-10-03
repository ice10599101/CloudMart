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
 * 退款成功消费者（T02）：REFUND_SUCCEEDED 事件驱动订单推进 REFUNDED。
 *
 * <p>LC03：仅消费 v2 信封事件（强制 eventId）——旧格式回退分支已随旧支付链路删除，
 * 非法形状拒绝消费，不绕过去重。Inbox 幂等：重复/乱序只推进一次
 * （与审批同步路径经 CAS REFUNDING→REFUNDED 收敛，QA07）。</p>
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
        // T02：投递侧（PaymentOutboxDelivery.deliver）写键为 eventType——原读 "event"
        // 恒为 null，退款成功事件全部被形状守卫拒绝 ACK，事件驱动退款推进从未工作过
        Object eventRaw = message.get("eventType");
        String event = eventRaw == null ? null : String.valueOf(eventRaw);
        if (!"REFUND_SUCCEEDED".equals(event)) {
            // LC03：非本业务事件（旧 PAYMENT_REFUND 生产者已删除）→ 拒绝，不执行任何业务
            log.warn("[T02] 非法退款事件形状（event={}），拒绝消费", event);
            return;
        }
        EventEnvelope envelope = PaymentResultConsumer.envelopeOf(message);
        Long orderId = Long.valueOf(envelope.aggregateId());
        if (inboxService.beginConsume(CONSUMER, envelope) == InboxService.ConsumeDecision.SKIP) {
            log.info("[ASYNC01] 退款成功事件已消费（幂等跳过） eventId={} orderId={}", envelope.eventId(), orderId);
            return;
        }
        try {
            orderService.notifyRefundSucceeded(orderId);
            // T11：售后案件回填（refundNo 关联；无关联案件时幂等无操作）
            Object refundNoRaw = ((Map<?, ?>) message.get("payload")) == null ? null
                    : ((java.util.Map<?, ?>) message.get("payload")).get("refundNo");
            if (refundNoRaw != null) {
                orderService.onAfterSaleRefundCompleted(String.valueOf(refundNoRaw));
            }
            inboxService.completeConsume(CONSUMER, envelope);
        } catch (Exception e) {
            inboxService.failConsume(CONSUMER, envelope, e.getMessage());
            throw e;
        }
    }
}
