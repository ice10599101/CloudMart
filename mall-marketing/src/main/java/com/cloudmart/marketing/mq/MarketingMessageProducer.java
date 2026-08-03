package com.cloudmart.marketing.mq;

import com.cloudmart.marketing.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营销服务 MQ 消息生产者。
 * 拼团成团后发送订单创建消息，超时未成团发送退款消息。
 */
@Component
@RequiredArgsConstructor
public class MarketingMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(MarketingMessageProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 拼团成功，发送创建订单消息。
     */
    public void sendGroupSuccessMessage(Long groupOrderId, Long activityId,
                                         Long productId, Long skuId,
                                         List<Long> memberUserIds) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("groupOrderId", groupOrderId);
            message.put("activityId", activityId);
            message.put("productId", productId);
            message.put("skuId", skuId);
            message.put("memberUserIds", memberUserIds);
            message.put("timestamp", System.currentTimeMillis());
            String destination = RocketMQConfig.MARKETING_TOPIC + ":" + RocketMQConfig.MARKETING_TAG_GROUP_SUCCESS;
            rocketMQTemplate.syncSend(destination, message);
            log.info("Sent group success message for groupOrder={}", groupOrderId);
        } catch (Exception e) {
            log.error("Failed to send group success message: {}", e.getMessage());
        }
    }

    /**
     * 拼团超时未成团，发送退款消息。
     *
     * <p>注意：{@code group-expired} tag 同时被 mall-marketing（标记过期）和 mall-payment（退款）消费，
     * 两个服务使用不同 ConsumerGroup，各自独立消费。
     */
    public void sendGroupExpiredMessage(Long groupOrderId, Long activityId,
                                         List<Long> memberUserIds) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("groupOrderId", groupOrderId);
            message.put("activityId", activityId);
            message.put("memberUserIds", memberUserIds);
            message.put("timestamp", System.currentTimeMillis());
            String destination = RocketMQConfig.MARKETING_TOPIC + ":" + RocketMQConfig.MARKETING_TAG_GROUP_EXPIRED;
            rocketMQTemplate.syncSend(destination, message);
            log.info("Sent group expired message for groupOrder={}", groupOrderId);
        } catch (Exception e) {
            log.error("Failed to send group expired message: {}", e.getMessage());
        }
    }
}
