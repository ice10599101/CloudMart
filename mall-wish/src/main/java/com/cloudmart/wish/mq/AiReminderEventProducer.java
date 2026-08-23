package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 提醒类事件生产者（Sprint 2.5，文档 2.5 预期管理/陪伴提醒）。
 *
 * <p>统一消息信封 {@link AiReminderMessage}，按 tag 区分链路：</p>
 * <ul>
 *   <li>{@code expected-guide}：预期管理 AI 引导（CHECKIN_REMINDER，
 *       通知含 3 选项：延长预期/调整目标/转入时间胶囊）</li>
 *   <li>{@code companion-reminder}：陪伴提醒（AI_REMINDER，每日 1 条）</li>
 * </ul>
 *
 * <p>发送失败 <b>Fail-Open</b>：记日志不阻断扫描主流程（状态流转已独立提交，
 * 推送缺口可由管理端通知记录核对；与 CapsuleEventProducer 策略一致）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReminderEventProducer {

    /** 通知类型：预期管理引导（文档 2.5 指定 CHECKIN_REMINDER） */
    public static final String NOTIFY_TYPE_EXPECTED_GUIDE = "CHECKIN_REMINDER";
    /** 通知类型：陪伴提醒 */
    public static final String NOTIFY_TYPE_COMPANION_REMINDER = "AI_REMINDER";
    /** 业务类型：预期管理通知（前端渲染 3 选项按钮依据） */
    public static final String BIZ_TYPE_EXPECTED_MANAGEMENT = "EXPECTED_MANAGEMENT";
    /** 业务类型：陪伴提醒 */
    public static final String BIZ_TYPE_COMPANION_REMINDER = "COMPANION_REMINDER";

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发布预期管理 AI 引导事件（mall-notification AiReminderEventConsumer 消费）。
     *
     * @param userId  心愿归属用户
     * @param wishId  到期心愿 ID（通知深链/埋点依据）
     * @param title   通知标题
     * @param content AI 引导文案（或模板降级文案）
     */
    public void publishExpectedGuide(Long userId, Long wishId, String title, String content) {
        publish(RocketMQConfig.WISH_TAG_EXPECTED_GUIDE,
                new AiReminderMessage(NOTIFY_TYPE_EXPECTED_GUIDE, userId, title, content,
                        wishId, BIZ_TYPE_EXPECTED_MANAGEMENT));
    }

    /**
     * 发布陪伴提醒事件（mall-notification AiReminderEventConsumer 消费）。
     *
     * @param userId  目标用户
     * @param title   通知标题
     * @param content 鼓励文案
     */
    public void publishCompanionReminder(Long userId, String title, String content) {
        publish(RocketMQConfig.WISH_TAG_COMPANION_REMINDER,
                new AiReminderMessage(NOTIFY_TYPE_COMPANION_REMINDER, userId, title, content,
                        null, BIZ_TYPE_COMPANION_REMINDER));
    }

    private void publish(String tag, AiReminderMessage message) {
        try {
            String destination = RocketMQConfig.WISH_TOPIC + ":" + tag;
            rocketMQTemplate.syncSend(destination, message);
            log.debug("AI提醒事件已发送, tag={}, userId={}", tag, message.userId());
        } catch (Exception e) {
            log.error("AI提醒事件发送失败（Fail-Open，不阻断扫描）, tag={}, userId={}",
                    tag, message.userId(), e);
        }
    }

    /** AI 提醒事件消息（mall-notification AiReminderEventConsumer 对齐）。 */
    public record AiReminderMessage(String notifyType, Long userId, String title,
                                    String content, Long bizId, String bizType) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
