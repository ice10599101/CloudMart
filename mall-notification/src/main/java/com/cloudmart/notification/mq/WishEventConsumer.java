package com.cloudmart.notification.mq;

import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 心愿域事件消费者（Sprint 2.4：时间胶囊到期待开启推送）。
 *
 * <p>生产端（mall-wish CapsuleEventProducer）仅在 SEALED→AVAILABLE CAS
 * 流转成功后发送，每胶囊至多一条（扫描幂等）；消费端假设消息可能重复投递
 * （RocketMQ at-least-once），通知落库以业务去重为准——推送记录允许
 * 重复展示，不产生用户侧副作用（站内信多条可见属可接受降级，
 * 管理端推送记录按 capsuleId 可核对）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.WISH_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_WISH_EVENT,
        selectorExpression = RocketMQConfig.WISH_TAG_CAPSULE_AVAILABLE
)
public class WishEventConsumer implements RocketMQListener<WishEventConsumer.CapsuleAvailableMessage> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(CapsuleAvailableMessage message) {
        try {
            String title = "时间胶囊到期啦";
            String content = "你封存的《" + (message.title() != null ? message.title() : "时间胶囊") + "》已到开启时间，来拆开这份过去的礼物吧";
            notificationService.sendNotificationToUser(
                    message.userId(), "CAPSULE_AVAILABLE", title, content, message.capsuleId(), "CAPSULE"
            );
            log.info("Capsule available notification sent: capsuleId={}, userId={}",
                    message.capsuleId(), message.userId());
        } catch (Exception e) {
            log.error("Failed to send capsule available notification: capsuleId={}", message.capsuleId(), e);
        }
    }

    /** 胶囊到期待开启事件消息（与 mall-wish CapsuleEventProducer 对齐）。 */
    public record CapsuleAvailableMessage(Long capsuleId, Long userId, String title) implements Serializable {}
}
