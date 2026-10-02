package com.cloudmart.order.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 拼团成团订单消费者（T10）：requestId = "group-{groupOrderId}-{userId}"
 * 稳定成员订单键——重复成团消息/事务回滚后重发不会重复建单（T03 幂等重放）。
 *
 * <p>价格权威在 createOrder 内部：按 groupOrderId 回查 mall-marketing 成团
 * 快照（成团状态 + 成员归属 + 拼团价），fail-closed，拼团价与普通价不混用。
 * 业务失败登记结果事件（运营可见）后 ACK；系统异常重抛触发 MQ 重试，
 * 重试耗尽进入死信由运营处置。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.MARKETING_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_GROUP_SUCCESS,
        selectorExpression = RocketMQConfig.MARKETING_TAG_GROUP_SUCCESS
)
public class GroupSuccessOrderConsumer implements RocketMQListener<Map<String, Object>> {

    private final OrderService orderService;
    private final OutboxService outboxService;

    @Override
    public void onMessage(Map<String, Object> message) {
        // T10：旧形状消息（无 eventId，随机键建单）拒绝——稳定订单键是幂等事实
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            log.warn("[T10] 成团消息缺少 eventId（旧形状），拒绝消费 keys={}", message.get("KEYS"));
            return;
        }
        // Outbox 信封形状：业务字段在嵌套 payload 中（与 MarketingOutboxDelivery 对称）
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        if (payload == null) {
            log.warn("[T10] 成团事件缺少 payload eventId={}", eventId);
            return;
        }
        Object groupOrderIdObj = payload.get("groupOrderId");
        if (groupOrderIdObj == null) {
            log.warn("[T10] 成团事件缺少 groupOrderId eventId={}", eventId);
            return;
        }
        Long groupOrderId = ((Number) groupOrderIdObj).longValue();
        Long activityId = ((Number) payload.get("activityId")).longValue();
        Long productId = ((Number) payload.get("productId")).longValue();
        Long skuId = ((Number) payload.get("skuId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> memberUserIds = (List<Number>) payload.get("memberUserIds");

        log.info("[T10] 处理成团建单 eventId={} groupOrderId={} skuId={} members={}",
                eventId, groupOrderId, skuId, memberUserIds.size());

        for (Number userIdNum : memberUserIds) {
            Long userId = userIdNum.longValue();
            // 稳定成员订单键：group-{groupOrderId}-{userId}
            String requestKey = "group-" + groupOrderId + "-" + userId;
            try {
                CreateOrderRequest.OrderItemInput item = new CreateOrderRequest.OrderItemInput(
                        productId, skuId, 1, null, null, null, BigDecimal.ONE
                );
                CreateOrderRequest request = new CreateOrderRequest(
                        requestKey, List.of(item), null, null, null, null, activityId, null, null, groupOrderId
                );
                OrderDTO order = orderService.createOrder(userId, request);
                log.info("[T10] 成团订单就绪 requestKey={} orderId={} userId={}",
                        requestKey, order.id(), userId);
            } catch (BusinessException e) {
                if ("DUPLICATE_REQUEST".equals(e.getCode())) {
                    // 重复成团消息：订单已存在，幂等吸收
                    log.info("[T10] 成团订单已存在（重复消息幂等吸收）requestKey={}", requestKey);
                    continue;
                }
                log.warn("[T10] 成团建单业务失败 requestKey={} code={} reason={}",
                        requestKey, e.getCode(), e.getMessage());
                outboxService.record(groupOrderFailedEvent(groupOrderId, userId, e.getMessage()));
            }
            // 非 BusinessException：重抛 → MQ 重试 → 死信（运营处置）；
            // 稳定 requestKey 保证重试不会重复建单
        }
    }

    /** 建单失败事件（稳定 eventId，运营处置依据） */
    private EventEnvelope groupOrderFailedEvent(Long groupOrderId, Long userId, String reason) {
        String safeReason = reason == null ? "成团建单失败" : reason.replace("\"", "'");
        String payload = "{\"groupOrderId\":" + groupOrderId + ",\"userId\":" + userId
                + ",\"reason\":\"" + safeReason + "\"}";
        return new EventEnvelope("group-order-failed-" + groupOrderId + "-" + userId,
                "GROUP_ORDER_FAILED", 1, String.valueOf(groupOrderId), 1,
                System.currentTimeMillis(), null, payload);
    }
}
