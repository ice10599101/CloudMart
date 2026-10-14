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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 拼团成团订单消费者（T10）：requestId = "group-{groupOrderId}-{userId}"
 * 稳定成员订单键——重复成团消息/事务回滚后重发不会重复建单（T03 幂等重放）。
 *
 * <p>C20：仅接受 v2 信封 + 强类型 {@link GroupSuccessPayload}；旧形状（缺 eventId/
 * payload/关键字段）直接抛错进入 MQ 重试/死信，不再静默 ACK。收货人严格使用参团时
 * 冻结的成员地址快照，不再回消费时用户当前默认地址（地址不随后续修改变化）。</p>
 *
 * <p>价格权威在 createOrder 内部：按 groupOrderId 回查 mall-marketing 成团
 * 快照（成团状态 + 成员归属 + 拼团价），fail-closed，拼团价与普通价不混用。
 * 业务失败登记结果事件（运营可见）后继续其余成员；系统异常重抛触发 MQ 重试，
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
        // C20：信封与载荷严格解析——缺 eventId/字段即抛错（MQ 重试→死信），稳定订单键是幂等事实
        EventEnvelope envelope = OrderEventMessages.requireEnvelope(message);
        GroupSuccessPayload payload = parsePayload(message, envelope.eventId());

        log.info("[T10] 处理成团建单 eventId={} groupOrderId={} skuId={} members={}",
                envelope.eventId(), payload.groupOrderId(), payload.skuId(),
                payload.memberUserIds().size());

        Map<Long, GroupSuccessPayload.Member> snapshotByUser = payload.members().stream()
                .filter(m -> m.userId() != null)
                .collect(Collectors.toMap(GroupSuccessPayload.Member::userId, Function.identity(),
                        (first, second) -> first));
        // 形状校验前置：memberUserIds 必须全部有对应快照（不得在部分建单后才发现事件损坏）
        for (Long userId : payload.memberUserIds()) {
            if (!snapshotByUser.containsKey(userId)) {
                throw new IllegalArgumentException(
                        "[T10] 成团事件缺少成员地址快照 eventId=" + envelope.eventId() + " userId=" + userId);
            }
        }

        for (Long userId : payload.memberUserIds()) {
            // 稳定成员订单键：group-{groupOrderId}-{userId}
            String requestKey = "group-" + payload.groupOrderId() + "-" + userId;
            GroupSuccessPayload.Member snapshot = snapshotByUser.get(userId);
            try {
                String[] receiver = requireReceiverSnapshot(snapshot, requestKey, userId);
                if (receiver == null) {
                    log.warn("[T11] 成员缺收货地址（补充地址动作） requestKey={} userId={}", requestKey, userId);
                    outboxService.record(groupOrderFailedEvent(payload.groupOrderId(), userId,
                            "ABSENT_ADDRESS:请补充收货地址后重试"));
                    continue;
                }
                CreateOrderRequest.OrderItemInput item = new CreateOrderRequest.OrderItemInput(
                        payload.productId(), payload.skuId(), 1, null, null, null, BigDecimal.ONE
                );
                CreateOrderRequest request = new CreateOrderRequest(
                        requestKey, List.of(item), receiver[0], receiver[1], receiver[2], null,
                        payload.activityId(), null, null, payload.groupOrderId()
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
                outboxService.record(groupOrderFailedEvent(payload.groupOrderId(), userId, e.getMessage()));
            }
            // 非 BusinessException：重抛 → MQ 重试 → 死信（运营处置）；
            // 稳定 requestKey 保证重试不会重复建单
        }
    }

    /** 载荷关键字段显式校验（Jackson 转换不替我们判空）。 */
    private GroupSuccessPayload parsePayload(Map<String, Object> message, String eventId) {
        GroupSuccessPayload payload = OrderEventMessages.payload(message, GroupSuccessPayload.class);
        if (payload.groupOrderId() == null || payload.activityId() == null
                || payload.productId() == null || payload.skuId() == null
                || payload.memberUserIds() == null || payload.memberUserIds().isEmpty()
                || payload.members() == null) {
            throw new IllegalArgumentException("[T10] GROUP_SUCCESS 载荷字段不全 eventId=" + eventId);
        }
        return payload;
    }

    /**
     * T11：收货人只取参团时冻结的快照——字段完整直接使用；成员显式 ABSENT_ADDRESS
     * 或快照字段不全返回 null（补充地址动作）。C20：不再回消费时当前默认地址。
     */
    private String[] requireReceiverSnapshot(GroupSuccessPayload.Member snapshot, String requestKey, Long userId) {
        String name = snapshot.receiverName();
        String phone = snapshot.receiverPhone();
        String address = snapshot.receiverAddress();
        if ("ABSENT_ADDRESS".equals(snapshot.orderTaskStatus())) {
            return null;
        }
        if (name != null && !name.isBlank() && phone != null && !phone.isBlank()
                && address != null && !address.isBlank()) {
            log.info("[T11] 成团建单使用参团地址快照 requestKey={} userId={}", requestKey, userId);
            return new String[]{name, phone, address};
        }
        return null;
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
