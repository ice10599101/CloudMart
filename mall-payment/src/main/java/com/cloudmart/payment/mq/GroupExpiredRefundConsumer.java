package com.cloudmart.payment.mq;

import com.cloudmart.payment.config.RocketMQConfig;
import com.cloudmart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 消费拼团过期事件，对未成团成员执行退款。
 *
 * <p>当前 PaymentService 仅提供 {@code refund(Long paymentId)}，无法通过
 * groupOrderId + userId 直接定位支付记录。在 PaymentService 新增
 * {@code refundByGroupOrder(Long groupOrderId, Long userId)} 方法之前，此处仅记录告警日志，
 * 待服务端方法就绪后补齐调用逻辑。</p>
 *
 * <p>注意：本消费者与 {@code mall-marketing} 的 {@code GroupExpiredListener} 共同订阅
 * {@code marketing-events:group-expired}，两者使用不同的 ConsumerGroup（{@code payment-group-expired-cg}
 * vs {@code marketing-group-expired-cg}），各自独立消费。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.MARKETING_TOPIC,
        consumerGroup = RocketMQConfig.CG_PAYMENT_GROUP_EXPIRED,
        selectorExpression = RocketMQConfig.MARKETING_TAG_GROUP_EXPIRED
)
public class GroupExpiredRefundConsumer implements RocketMQListener<Map<String, Object>> {

    private final PaymentService paymentService;

    @Override
    public void onMessage(Map<String, Object> message) {
        Long groupOrderId = ((Number) message.get("groupOrderId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> memberUserIds = (List<Number>) message.get("memberUserIds");

        log.info("Processing group expired refund: groupOrderId={}, members={}", groupOrderId, memberUserIds.size());

        for (Number userIdNum : memberUserIds) {
            Long userId = userIdNum.longValue();
            try {
                // TODO: PaymentService 暂未提供 refundByGroupOrder(Long groupOrderId, Long userId) 方法，
                //  现有 refund(Long paymentId) 需要 paymentId，而本消息仅包含 groupOrderId + userId，
                //  无法在缺少 paymentId 的情况下完成退款。待 PaymentService 补充按拼团订单退款的方法后，
                //  替换为：paymentService.refundByGroupOrder(groupOrderId, userId);
                log.warn("refundByGroupOrder not implemented on PaymentService, skip refund: groupOrderId={}, userId={}",
                        groupOrderId, userId);
            } catch (Exception e) {
                log.error("Failed to refund: groupOrderId={}, userId={}", groupOrderId, userId, e);
            }
        }
    }
}
