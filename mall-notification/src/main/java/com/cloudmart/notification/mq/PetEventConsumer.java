package com.cloudmart.notification.mq;

import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;

/**
 * 宠物域事件消费者（社区宠物模块：打工/读书/捞瓶/对战/升级/主动消息）。
 *
 * <p>文案已在 mall-pet 侧按宠物口吻生成，本消费者原样落 notifications 表
 * （type=PET，bizType=具体子类型如 PET_BOTTLE_CAUGHT）+ WebSocket 推送。</p>
 *
 * <p>B19 契约：消息体 ID 一律字符串（userId/bizId，与 mall-pet 生产端对齐）；
 * eventId 为业务事件唯一键（TYPE:实例），消费者按其去重——MQ at-least-once 的
 * 重复投递不再产生第二条站内信（uk_notification_event 唯一索引兜底）。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = RocketMQConfig.PET_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_PET_EVENT
)
public class PetEventConsumer implements RocketMQListener<PetEventConsumer.PetEventMessage> {

    private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final NotificationService notificationService;

    public PetEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onMessage(PetEventMessage message) {
        try {
            // B19：eventId 幂等落库（唯一键兜底重投与并发双消费）
            notificationService.sendPetEventNotification(
                    Long.valueOf(message.userId()), message.eventId(), message.reminderType(),
                    message.title(), message.content(), parseBizId(message.bizId()));
            log.info("Pet event notification sent: userId={}, type={}", message.userId(), message.reminderType());
        } catch (Exception e) {
            log.error("Failed to send pet event notification: userId={}, type={}",
                    message.userId(), message.reminderType(), e);
        }
    }

    private Long parseBizId(String bizId) {
        if (bizId == null || bizId.isBlank() || "0".equals(bizId)) {
            return 0L;
        }
        try {
            return Long.valueOf(bizId);
        } catch (NumberFormatException e) {
            log.warn("宠物事件 bizId 非法数字, bizId={}", bizId);
            return 0L;
        }
    }

    /**
     * 宠物事件消息（与 mall-pet PetEventProducer.PetEventMessage 字段对齐；ID 为字符串，B07/B19）。
     */
    public record PetEventMessage(
            String eventId,
            String userId,
            String reminderType,
            String title,
            String content,
            String bizId,
            String bizType
    ) implements Serializable {
    }
}
