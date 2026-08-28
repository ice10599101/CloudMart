package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * 擦肩而过事件生产者（Sprint 3.3：信笺投递通知 + 匿名互动通知）。
 *
 * <p>消息信封与 AiReminderEventProducer.AiReminderMessage 字段一致，
 * 由 mall-notification AiReminderEventConsumer 统一消费落站内信
 * （tag encounter-letter，notifyType=ENCOUNTER_LETTER）。</p>
 *
 * <p>匿名验收：互动通知内容不含 letterId/userId/昵称——对方无法反查
 * 信笺归属，仅感知"有一位擦肩而过的人"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncounterEventProducer {

    /** 通知类型：信笺投递/匿名互动（文档通知类型清单 ENCOUNTER_LETTER） */
    public static final String NOTIFY_TYPE_ENCOUNTER = "ENCOUNTER_LETTER";
    /** 业务类型：信笺投递 */
    public static final String BIZ_TYPE_ENCOUNTER_LETTER = "ENCOUNTER_LETTER";
    /** 业务类型：匿名互动 */
    public static final String BIZ_TYPE_ENCOUNTER_INTERACTION = "ENCOUNTER_INTERACTION";

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 信笺投递通知（"你和一位同路人擦肩而过，一封信笺已送达"）。
     */
    public void publishLetterDelivered(Long ownerUserId, Long letterId) {
        publish(new EncounterNotifyMessage(NOTIFY_TYPE_ENCOUNTER, ownerUserId,
                "一封相遇信笺已送达",
                "你和一位想实现同一个愿望的人擦肩而过，拆开看看这份缘分吧",
                null, BIZ_TYPE_ENCOUNTER_LETTER));
    }

    /**
     * 匿名互动通知（对方收到"有一位擦肩而过的人为你点亮/祝福了心愿"；
     * 不含 letterId/userId——匿名性验收）。
     */
    public void publishAnonInteraction(Long peerUserId, boolean isLight) {
        String content = isLight
                ? "有一位擦肩而过的人为你点亮了心愿 ⭐"
                : "有一位擦肩而过的人为你送来了祝福 💛";
        publish(new EncounterNotifyMessage(NOTIFY_TYPE_ENCOUNTER, peerUserId,
                "擦肩而过的温柔",
                content,
                null, BIZ_TYPE_ENCOUNTER_INTERACTION));
    }

    private void publish(EncounterNotifyMessage message) {
        try {
            String destination = RocketMQConfig.WISH_TOPIC + ":" + RocketMQConfig.WISH_TAG_ENCOUNTER_LETTER;
            rocketMQTemplate.syncSend(destination, message);
            log.debug("擦肩而过通知已发送, userId={}", message.userId());
        } catch (Exception e) {
            log.error("擦肩而过通知发送失败（Fail-Open）, userId={}", message.userId(), e);
        }
    }

    /** 擦肩而过通知消息（mall-notification AiReminderEventConsumer 消费，字段对齐）。 */
    public record EncounterNotifyMessage(String notifyType, Long userId, String title,
                                         String content, Long bizId, String bizType) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
