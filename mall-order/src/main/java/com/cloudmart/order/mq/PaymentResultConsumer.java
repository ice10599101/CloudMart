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
 * 支付结果消费者（T05）：PAYMENT_SUCCEEDED 事件驱动订单推进 PAID 的唯一 MQ 路径。
 *
 * <p>C20：仅接受 Outbox v2 信封（eventId/eventType/schemaVersion 必填）+ 强类型
 * {@link PaymentSucceededPayload}——旧顶层 orderId/event 分支、缺字段兜底全部删除，
 * 非法形状直接抛错由 MQ 重试/死信处置，不绕过去重。</p>
 *
 * <p>金额/币种校验（T05）：载荷中的渠道实付与币种交由 {@code applyPaymentSucceeded}
 * 与订单应付核对，不一致拒绝推进。</p>
 *
 * <p>begin 的消费记录与业务变更同一事务——业务回滚则记录一并回滚，MQ 重投后重试；
 * 重复投递命中已处理记录直接跳过。</p>
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
        EventEnvelope envelope = OrderEventMessages.requireEnvelope(message);
        if (!"PAYMENT_SUCCEEDED".equals(envelope.eventType())) {
            // tag 内的历史事件名（如旧 PAYMENT_SUCCESS）不再处理
            log.info("[T05] 非 PAYMENT_SUCCEEDED 事件忽略 eventType={}", envelope.eventType());
            return;
        }
        PaymentSucceededPayload payload = OrderEventMessages.payload(message, PaymentSucceededPayload.class);
        if (payload.orderId() == null || payload.merchantPaymentNo() == null
                || payload.merchantPaymentNo().isBlank()) {
            // 载荷缺关键事实：形状错误，拒绝消费（不推断、不补默认）
            throw new IllegalArgumentException("[T05] PAYMENT_SUCCEEDED 载荷缺少 orderId/merchantPaymentNo: "
                    + envelope.eventId());
        }
        log.info("[T05] 收到支付成功事件, orderId={}, amount={}, currency={}, paymentNo={}, eventId={}",
                payload.orderId(), payload.amount(), payload.currency(),
                payload.merchantPaymentNo(), envelope.eventId());

        if (inboxService.beginConsume(CONSUMER, envelope) == InboxService.ConsumeDecision.SKIP) {
            log.info("[ASYNC01] 支付事件已消费（幂等跳过） eventId={} orderId={}",
                    envelope.eventId(), payload.orderId());
            return;
        }
        try {
            orderService.applyPaymentSucceeded(payload.orderId(), payload.amount(), payload.currency(),
                    payload.merchantPaymentNo());
            inboxService.completeConsume(CONSUMER, envelope);
        } catch (Exception e) {
            inboxService.failConsume(CONSUMER, envelope, e.getMessage());
            throw e;
        }
    }
}
