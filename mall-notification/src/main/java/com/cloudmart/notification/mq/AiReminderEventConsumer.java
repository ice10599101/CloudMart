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
 * AI 提醒事件消费者（Sprint 2.5：预期管理引导 + 陪伴提醒推送）。
 *
 * <p>生产端覆盖四类 tag：mall-wish AiReminderEventProducer 的
 * {@code expected-guide}（CHECKIN_REMINDER，心愿到期 AI 引导，通知带
 * 延长预期/调整目标/转入时间胶囊 3 选项，bizType=EXPECTED_MANAGEMENT，
 * bizId=心愿 ID 供前端埋点与深链）与 {@code companion-reminder}
 * （AI_REMINDER，每日 1 条鼓励）；mall-wish MatchEventProducer 的
 * {@code squad-remind}（AI_REMINDER，同路人互相提醒，bizType=SQUAD_REMIND）
 * 与 {@code squad-event}（SYSTEM，被踢/解散通知，bizType=SQUAD_EVENT）；
 * LegacyEventProducer 的 {@code legacy-push}（WISH_FULFILL，"你的同愿实现了"
 * 传承推送，bizType=FULFILLMENT_LEGACY，Sprint 2.7）。</p>
 *
 * <p>重复投递语义与 WishEventConsumer 一致：mall-wish 侧已用 Redis
 * 按用户×日限频去重（预期 3 条/日、陪伴 1 条/日），消费端仅剩 MQ 重投
 * 场景，站内信重复展示属可接受降级（无用户侧副作用），不做额外去重。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.WISH_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_AI_REMINDER,
        selectorExpression = RocketMQConfig.WISH_TAG_EXPECTED_GUIDE
                + " || " + RocketMQConfig.WISH_TAG_COMPANION_REMINDER
                + " || " + RocketMQConfig.WISH_TAG_SQUAD_REMIND
                + " || " + RocketMQConfig.WISH_TAG_SQUAD_EVENT
                + " || " + RocketMQConfig.WISH_TAG_ENCOUNTER_LETTER
                + " || " + RocketMQConfig.WISH_TAG_LEGACY_PUSH
)
public class AiReminderEventConsumer implements RocketMQListener<AiReminderEventConsumer.AiReminderMessage> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(AiReminderMessage message) {
        try {
            notificationService.sendNotificationToUser(
                    message.userId(),
                    message.notifyType(),
                    message.title(),
                    message.content(),
                    message.bizId(),
                    message.bizType()
            );
            log.info("AI reminder notification sent: notifyType={}, userId={}, bizId={}",
                    message.notifyType(), message.userId(), message.bizId());
        } catch (Exception e) {
            log.error("Failed to send AI reminder notification: notifyType={}, userId={}, bizId={}",
                    message.notifyType(), message.userId(), message.bizId(), e);
        }
    }

    /** AI 提醒事件消息（与 mall-wish AiReminderEventProducer.AiReminderMessage 字段对齐）。 */
    public record AiReminderMessage(String notifyType, Long userId, String title,
                                    String content, Long bizId, String bizType) implements Serializable {
    }
}
