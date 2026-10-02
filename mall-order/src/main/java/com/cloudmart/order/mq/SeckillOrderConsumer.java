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
 * 秒杀下单消费者（T09）：requestId 由提交时生成并贯穿全链路——订单
 * request_key 即 requestId，100 次重复消息仍只建一单（T03 幂等重放）。
 *
 * <p>失败分类：业务失败（库存不足/报价失效等）→ 登记结果回写事件（终态失败，
 * 秒杀侧释放占用）后 ACK；系统异常 → 重抛触发 MQ 重试，重试耗尽进入死信由
 * 运营处置。请求保持 PENDING 由恢复任务按事实对账，绝不吞错假成功。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.SECKILL_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_SECKILL,
        selectorExpression = RocketMQConfig.SECKILL_TAG_ORDER
)
public class SeckillOrderConsumer implements RocketMQListener<Map<String, Object>> {

    private final OrderService orderService;
    private final OutboxService outboxService;

    @Override
    public void onMessage(Map<String, Object> message) {
        String requestId = (String) message.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            // T09：旧形状消息（无 requestId）拒绝——随机键会破坏"同请求一订单"事实
            log.warn("[T09] 秒杀消息缺少 requestId（旧形状），拒绝消费 keys={}", message.get("KEYS"));
            return;
        }
        Long userId = ((Number) message.get("userId")).longValue();
        Long activityId = ((Number) message.get("activityId")).longValue();
        Long skuId = ((Number) message.get("skuId")).longValue();
        Long productId = ((Number) message.get("seckillProductId")).longValue();
        Integer quantity = message.get("quantity") != null ? ((Number) message.get("quantity")).intValue() : 1;
        Object priceObj = message.get("seckillPrice");
        BigDecimal declaredPrice = priceObj == null ? null : new BigDecimal(String.valueOf(priceObj));

        log.info("[T09] 处理秒杀下单 requestId={} userId={} activityId={} skuId={}",
                requestId, userId, activityId, skuId);

        try {
            // 价格仅作占位：createOrder 内部以 mall-seckill 冻结快照校验并覆盖（活动报价权威）
            CreateOrderRequest.OrderItemInput item = new CreateOrderRequest.OrderItemInput(
                    productId, skuId, quantity, null, null, null,
                    declaredPrice != null ? declaredPrice : BigDecimal.ONE
            );
            CreateOrderRequest request = new CreateOrderRequest(
                    requestId, List.of(item), null, null, null, null, activityId, null, requestId
            );
            OrderDTO order = orderService.createOrder(userId, request);
            // SUCCESS 结果事件由 createOrder 与订单同事务登记（Outbox），此处不再重复发送
            log.info("[T09] 秒杀订单已建 requestId={} orderId={} userId={}", requestId, order.id(), userId);
        } catch (BusinessException e) {
            // 业务失败：终态结果回写（秒杀侧落 FAILED 并释放占用），ACK 不重试
            log.warn("[T09] 秒杀建单业务失败 requestId={} code={} reason={}",
                    requestId, e.getCode(), e.getMessage());
            outboxService.record(seckillFailedEvent(requestId, e.getMessage()));
        }
        // 非 BusinessException：重抛 → MQ 重试 → 重试耗尽死信（运营处置）；
        // 订单 request_key 幂等保证重试不会重复建单
    }

    /** 稳定 eventId：重试/重复发布幂等去重 */
    private EventEnvelope seckillFailedEvent(String requestId, String reason) {
        String safeReason = reason == null ? "秒杀失败" : reason.replace("\"", "'");
        String payload = "{\"requestId\":\"" + requestId + "\",\"success\":false"
                + ",\"reason\":\"" + safeReason + "\"}";
        return new EventEnvelope("seckill-result-" + requestId,
                "SECKILL_RESULT", 1, requestId, 1, System.currentTimeMillis(), requestId, payload);
    }
}
