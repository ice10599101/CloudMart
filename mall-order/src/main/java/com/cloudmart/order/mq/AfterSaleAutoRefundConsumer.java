package com.cloudmart.order.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 售后自动退款消费者（T11 切片二 C）：质检 PASSED 的 Outbox 事件驱动
 * 系统代发退款流转——requestRefund（订单 CAS 推入 REFUNDING）+
 * approveRefund（提交 T02 渠道退款单），免人工二次操作。
 *
 * <p>Inbox 幂等：重复消费只流转一次；退款失败（如渠道拒绝）抛出重试，
 * 重试耗尽进死信由运营在售后案件上人工处置（案件状态保留 APPROVED 可查）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.PAYMENT_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_AFTER_SALE,
        selectorExpression = RocketMQConfig.TAG_AFTER_SALE
)
public class AfterSaleAutoRefundConsumer implements RocketMQListener<Map<String, Object>> {

    static final String CONSUMER = "order-after-sale-auto-refund";
    static final String EVENT_TYPE = "AFTER_SALE_INSPECT_PASSED";

    private final InboxService inboxService;
    private final AfterSaleCaseMapper caseMapper;
    private final com.cloudmart.order.service.OrderService orderService;

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            log.warn("[T11] 售后自动退款事件缺少 eventId（旧形状），拒绝消费");
            return;
        }
        if (!EVENT_TYPE.equals(message.get("eventType"))) {
            log.warn("[T11] 售后自动退款消费者收到未知事件类型 {}", message.get("eventType"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        Long caseId = payload == null || payload.get("caseId") == null
                ? null : ((Number) payload.get("caseId")).longValue();
        if (caseId == null) {
            log.warn("[T11] 售后自动退款事件缺少 caseId eventId={}", eventId);
            return;
        }

        if (inboxService.beginConsume(CONSUMER,
                new EventEnvelope(eventId, EVENT_TYPE, 1, String.valueOf(caseId), 1, 0L, null, null))
                == InboxService.ConsumeDecision.SKIP) {
            return;
        }
        try {
            AfterSaleCase entity = caseMapper.selectById(caseId);
            if (entity == null) {
                log.warn("[T11] 售后自动退款：案件不存在 caseId={}", caseId);
                inboxService.completeConsume(CONSUMER,
                        new EventEnvelope(eventId, EVENT_TYPE, 1, String.valueOf(caseId), 1, 0L, null, null));
                return;
            }
            if (!AfterSaleCase.STATUS_APPROVED.equals(entity.getStatus())) {
                // 已被人工推进/关闭：幂等吸收
                log.info("[T11] 售后案件非 APPROVED，自动退款跳过 caseId={} status={}",
                        caseId, entity.getStatus());
                inboxService.completeConsume(CONSUMER,
                        new EventEnvelope(eventId, EVENT_TYPE, 1, String.valueOf(caseId), 1, 0L, null, null));
                return;
            }
            // 系统代发：订单推入 REFUNDING（系统原因码）→ 提交 T02 渠道退款
            orderService.requestRefund(entity.getUserId(), entity.getOrderId(),
                    "售后案件 " + entity.getCaseNo() + " 质检通过自动退款");
            orderService.approveRefundSystem(entity.getOrderId());
            inboxService.completeConsume(CONSUMER,
                    new EventEnvelope(eventId, EVENT_TYPE, 1, String.valueOf(caseId), 1, 0L, null, null));
            log.info("[T11] 售后自动退款流转完成 caseId={} orderId={}", caseId, entity.getOrderId());
        } catch (Exception e) {
            inboxService.failConsume(CONSUMER,
                    new EventEnvelope(eventId, EVENT_TYPE, 1, String.valueOf(caseId), 1, 0L, null, null),
                    e.getMessage());
            throw e;
        }
    }
}
