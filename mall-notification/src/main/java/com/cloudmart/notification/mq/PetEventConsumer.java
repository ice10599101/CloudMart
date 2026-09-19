package com.cloudmart.notification.mq;

import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 宠物域事件消费者（社区宠物模块：打工/读书/捞瓶/对战/升级/主动消息）。
 *
 * <p>文案已在 mall-pet 侧按宠物口吻生成，本消费者原样落 notifications 表
 * （type=PET，bizType=具体子类型如 PET_BOTTLE_CAUGHT）+ WebSocket 推送。
 * 消费假设消息可能重复（at-least-once）：重复推送仅多条站内信可见，
 * 无用户侧副作用，与 WishEventConsumer 同容错口径。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = RocketMQConfig.PET_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_PET_EVENT
)
public class PetEventConsumer implements RocketMQListener<PetEventConsumer.PetEventMessage> {

    private final NotificationService notificationService;

    public PetEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onMessage(PetEventMessage message) {
        try {
            notificationService.sendNotificationToUser(
                    message.userId(), "PET", message.title(), message.content(),
                    message.bizId(), message.reminderType()
            );
            log.info("Pet event notification sent: userId={}, type={}", message.userId(), message.reminderType());
        } catch (Exception e) {
            log.error("Failed to send pet event notification: userId={}, type={}",
                    message.userId(), message.reminderType(), e);
        }
    }

    /** 宠物事件消息（与 mall-pet PetEventProducer.PetEventMessage 字段对齐） */
    public record PetEventMessage(
            Long userId,
            String reminderType,
            String title,
            String content,
            Long bizId,
            String bizType
    ) implements Serializable {
    }
}
