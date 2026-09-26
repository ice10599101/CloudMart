package com.cloudmart.pet.mq;

import com.cloudmart.pet.config.RocketMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 宠物事件生产者（pet-events topic，tag 按事件类型 kebab-case）。
 *
 * <p>消费方 mall-notification 落 notifications 表 + WebSocket 推送。
 * 直接通道 {@link #publish} 仅保留给展示型提醒（Fail-Open，缺失可由主动消息兜底）；
 * 奖励/业务事实类通知一律走 PetOutboxService（事务后可靠投递 + 消费者 eventId 去重）。</p>
 *
 * <p>B07 契约：消息体 ID 一律字符串（userId/bizId），避免 JS 端 Number 解析精度丢失；
 * mall-notification 侧按同一契约解析。</p>
 */
@Slf4j
@Component
public class PetEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public PetEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 宠物事件消息体（与 mall-notification PetEventConsumer 字段对齐）。
     *
     * @param eventId      业务事件唯一键（TYPE:实例，消费者按其去重，MQ at-least-once 兜底）
     * @param userId       目标用户 ID（字符串，B07）
     * @param reminderType 提醒子类型（落 notifications.biz_type）: PET_WORK_COMPLETED/PET_BOTTLE_CAUGHT/...
     * @param bizId        业务实例 ID（字符串，B07）
     */
    public record PetEventMessage(
            String eventId,
            String userId,
            String reminderType,
            String title,
            String content,
            String bizId,
            String bizType
    ) {
    }

    /**
     * 发送宠物事件（提醒文案已按宠物口吻在服务端生成，通知服务原样落库）。
     * 发送失败仅记日志（Fail-Open）：业务主流程（奖励/状态）已落库。
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

    /**
     * 尝试发送并回报结果（outbox 发送器用）：成功 true，失败 false（发件箱记录退避重试）。
     */
    public boolean tryPublish(String tag, PetEventMessage message) {
        try {
            String destination = RocketMQConfig.PET_TOPIC + ":" + tag;
            rocketMQTemplate.syncSend(destination, message);
            return true;
        } catch (Exception e) {
            log.error("宠物事件发送失败（outbox 将退避重试）: tag={}, eventId={}, userId={}",
                    tag, message.eventId(), message.userId(), e);
            return false;
        }
    }
}
