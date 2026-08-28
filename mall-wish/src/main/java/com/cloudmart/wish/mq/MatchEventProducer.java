package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * 同愿小队通知事件生产者（Sprint 2.6：互相提醒/被踢/解散通知）。
 *
 * <p>消息信封与 AiReminderEventProducer.AiReminderMessage 字段一致，
 * 由 mall-notification AiReminderEventConsumer 统一消费落站内信
 * （selectorExpression 覆盖 squad-remind / squad-event 两 tag）。</p>
 *
 * <p>发送失败 <b>Fail-Open</b>：记日志不阻断主流程（提醒/通知为
 * 展示性副作用，与 Capsule/AiReminder 生产者策略一致）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEventProducer {

    /** 通知类型：同路人互相提醒（复用 AI_REMINDER 类型，站内信分类一致） */
    public static final String NOTIFY_TYPE_SQUAD_REMIND = "AI_REMINDER";
    /** 通知类型：小队成员变动（被踢/解散，复用 SYSTEM 类型） */
    public static final String NOTIFY_TYPE_SQUAD_EVENT = "SYSTEM";
    /** 业务类型：互相提醒（通知中心渲染依据） */
    public static final String BIZ_TYPE_SQUAD_REMIND = "SQUAD_REMIND";
    /** 业务类型：小队成员变动 */
    public static final String BIZ_TYPE_SQUAD_EVENT = "SQUAD_EVENT";

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发布互相提醒（发送者 → 未打卡组员）。
     */
    public void publishSquadRemind(Long targetUserId, Long groupId, String groupKeyword, String senderNickname) {
        String nickname = senderNickname == null || senderNickname.isBlank() ? "同路人" : senderNickname;
        publish(RocketMQConfig.WISH_TAG_SQUAD_REMIND,
                new MatchNotifyMessage(NOTIFY_TYPE_SQUAD_REMIND, targetUserId,
                        "同路人监督提醒",
                        nickname + " 在小组「" + groupKeyword + "」里喊你回来打卡啦",
                        groupId, BIZ_TYPE_SQUAD_REMIND));
    }

    /**
     * 发布小队成员变动通知（被踢/解散，接收者为受影响成员）。
     */
    public void publishSquadEvent(Long targetUserId, Long groupId, String title, String content) {
        publish(RocketMQConfig.WISH_TAG_SQUAD_EVENT,
                new MatchNotifyMessage(NOTIFY_TYPE_SQUAD_EVENT, targetUserId,
                        title, content, groupId, BIZ_TYPE_SQUAD_EVENT));
    }

    private void publish(String tag, MatchNotifyMessage message) {
        try {
            String destination = RocketMQConfig.WISH_TOPIC + ":" + tag;
            rocketMQTemplate.syncSend(destination, message);
            log.debug("小队通知事件已发送, tag={}, userId={}", tag, message.userId());
        } catch (Exception e) {
            log.error("小队通知事件发送失败（Fail-Open，不阻断主流程）, tag={}, userId={}",
                    tag, message.userId(), e);
        }
    }

    /** 小队通知消息（mall-notification AiReminderEventConsumer 消费，字段对齐）。 */
    public record MatchNotifyMessage(String notifyType, Long userId, String title,
                                     String content, Long bizId, String bizType) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
