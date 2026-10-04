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
    private final com.cloudmart.order.feign.UserAddressFeignClient userAddressFeignClient;

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

        // T11：成员地址快照（参团时冻结）——建单用快照，不被建单时刻的新默认地址替换；
        // ABSENT_ADDRESS 成员返回"补充地址"动作（建单失败事件 reason 稳定可运营处置）
        Map<Long, Map<String, Object>> snapshots = new java.util.HashMap<>();
        Object membersRaw = payload.get("members");
        if (membersRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Object uid = m.get("userId");
                    if (uid instanceof Number n) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> casted = (Map<String, Object>) m;
                        snapshots.put(n.longValue(), casted);
                    }
                }
            }
        }

        for (Number userIdNum : memberUserIds) {
            Long userId = userIdNum.longValue();
            // 稳定成员订单键：group-{groupOrderId}-{userId}
            String requestKey = "group-" + groupOrderId + "-" + userId;
            try {
                // T11：优先参团快照；旧事件无快照回退默认地址（兼容在途消息）
                String[] receiver = resolveReceiver(userId, requestKey, snapshots.get(userId));
                if (receiver == null) {
                    log.warn("[T11] 成员缺收货地址（补充地址动作） requestKey={} userId={}",
                            requestKey, userId);
                    outboxService.record(groupOrderFailedEvent(groupOrderId, userId,
                            "ABSENT_ADDRESS:请补充收货地址后重试"));
                    continue;
                }
                CreateOrderRequest.OrderItemInput item = new CreateOrderRequest.OrderItemInput(
                        productId, skuId, 1, null, null, null, BigDecimal.ONE
                );
                CreateOrderRequest request = new CreateOrderRequest(
                        requestKey, List.of(item), receiver[0], receiver[1], receiver[2], null,
                        activityId, null, null, groupOrderId
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

    /**
     * T11：收货人解析——快照优先（参团时冻结）；快照缺失/字段不全返回 null
     * （补充地址动作）；旧事件无快照回退默认地址（兼容在途消息）。
     */
    private String[] resolveReceiver(Long userId, String requestKey, Map<String, Object> snapshot) {
        if (snapshot != null) {
            String name = snapshot.get("receiverName") == null ? null
                    : String.valueOf(snapshot.get("receiverName"));
            String phone = snapshot.get("receiverPhone") == null ? null
                    : String.valueOf(snapshot.get("receiverPhone"));
            String address = snapshot.get("receiverAddress") == null ? null
                    : String.valueOf(snapshot.get("receiverAddress"));
            if (snapshot.get("orderTaskStatus") != null
                    && "ABSENT_ADDRESS".equals(String.valueOf(snapshot.get("orderTaskStatus")))) {
                return null;
            }
            if (name != null && !name.isBlank() && phone != null && !phone.isBlank()
                    && address != null && !address.isBlank()) {
                log.info("[T11] 成团建单使用参团地址快照 requestKey={} userId={}", requestKey, userId);
                return new String[]{name, phone, address};
            }
            return null;
        }
        // 兼容在途旧事件：默认地址（T10 语义）
        try {
            var resp = userAddressFeignClient.getDefaultAddress(userId);
            if (resp == null || !resp.success() || resp.data() == null) {
                return null;
            }
            var addr = resp.data();
            log.info("[T10] 系统单收货人已解析 requestKey={} userId={} addressId={}", requestKey, userId, addr.id());
            return new String[]{addr.receiverName(), addr.phone(), addr.fullAddress()};
        } catch (BusinessException e) {
            return null;
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
