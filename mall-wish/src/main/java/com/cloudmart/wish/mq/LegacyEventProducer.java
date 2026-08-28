package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * 还愿传承事件生产者（Sprint 2.7：还愿后定向推送曾同求用户）。
 *
 * <p>消息信封与 AiReminderEventProducer.AiReminderMessage 字段一致，
 * 由 mall-notification AiReminderEventConsumer 统一消费落站内信
 * （tag legacy-push，notifyType=WISH_FULFILL，bizType=FULFILLMENT_LEGACY，
 * bizId=心愿 ID 供前端深链心愿详情查看还愿故事）。</p>
 *
 * <p>发送失败 <b>Fail-Open</b>：记日志不阻断主流程（传承通知为展示性
 * 副作用，pushed_count 口径为尝试推送数）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyEventProducer {

    /** 通知类型：心愿被还愿（文档通知类型清单 WISH_FULFILL） */
    public static final String NOTIFY_TYPE_LEGACY_PUSH = "WISH_FULFILL";
    /** 业务类型：传承推送（前端渲染"你的同愿实现了"温暖样式依据） */
    public static final String BIZ_TYPE_LEGACY = "FULFILLMENT_LEGACY";

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发布传承通知（同求用户收到"你的同愿实现了"）。
     *
     * @param targetUserId 曾同求用户
     * @param wishId       心愿 ID（深链）
     * @param wishTitle    心愿标题
     * @param storySummary 还愿故事摘要（≤60 字）
     * @param authorMessage 作者附言（可空）
     */
    public void publishLegacyPush(Long targetUserId, Long wishId, String wishTitle,
                                  String storySummary, String authorMessage) {
        StringBuilder content = new StringBuilder("你的同愿实现了 🎉")
                .append("「").append(wishTitle).append("」已实现：").append(storySummary);
        if (authorMessage != null && !authorMessage.isBlank()) {
            content.append("。来自作者的留言：").append(authorMessage.trim());
        }
        publish(new LegacyNotifyMessage(NOTIFY_TYPE_LEGACY_PUSH, targetUserId,
                "你的同愿实现了", content.toString(), wishId, BIZ_TYPE_LEGACY));
    }

    private void publish(LegacyNotifyMessage message) {
        try {
            String destination = RocketMQConfig.WISH_TOPIC + ":" + RocketMQConfig.WISH_TAG_LEGACY_PUSH;
            rocketMQTemplate.syncSend(destination, message);
            log.debug("传承通知已发送, userId={}, wishId={}", message.userId(), message.bizId());
        } catch (Exception e) {
            log.error("传承通知发送失败（Fail-Open，不阻断传承主流程）, userId={}, wishId={}",
                    message.userId(), message.bizId(), e);
        }
    }

    /** 传承通知消息（mall-notification AiReminderEventConsumer 消费，字段对齐）。 */
    public record LegacyNotifyMessage(String notifyType, Long userId, String title,
                                      String content, Long bizId, String bizType) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
