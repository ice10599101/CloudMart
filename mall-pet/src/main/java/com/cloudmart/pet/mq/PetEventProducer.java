package com.cloudmart.pet.mq;

import com.cloudmart.pet.config.RocketMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 宠物事件生产者（pet-events topic，tag 按事件类型 kebab-case）。
 *
 * <p>消费方 mall-notification 落 notifications 表 + WebSocket 推送。
 * 通知类消息发送失败仅记日志（Fail-Open）：业务主流程（奖励/状态）已落库，
 * 通知缺失可由用户下次打开宠物页的主动消息触发器兜底补偿，不阻断、不重试风暴。</p>
 */
@Slf4j
@Component
public class PetEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public PetEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送宠物事件（提醒文案已按宠物口吻在服务端生成，通知服务原样落库）。
     */
    public void publish(String tag, PetEventMessage message) {
        try {
            String destination = RocketMQConfig.PET_TOPIC + ":" + tag;
            rocketMQTemplate.syncSend(destination, message);
        } catch (Exception e) {
            log.error("宠物事件发送失败（Fail-Open，不阻断业务）: tag={}, userId={}, type={}",
                    tag, message.userId(), message.reminderType(), e);
        }
    }

    /** 宠物事件消息体（与 mall-notification PetEventConsumer 字段对齐） */
    public record PetEventMessage(
            Long userId,
            /** 提醒子类型（落 notifications.biz_type）: PET_WORK_COMPLETED/PET_BOTTLE_CAUGHT/... */
            String reminderType,
            String title,
            String content,
            Long bizId,
            String bizType
    ) implements Serializable {
    }
}
