package com.cloudmart.notification.mq;

import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 小答软件域事件消费者（方案 9.4/10）。
 *
 * <p>生产端 mall-software 同事务经 Outbox 中继投递至 software-events topic；
 * 消费端按 eventId + recipientId 幂等（notifications 唯一键），MQ at-least-once
 * 重投不产生重复站内信；失败重抛触发 MQ 重试。文案不包含下载链接、提取码或
 * 评论全文；bizId 供前端跳转详情。</p>
 *
 * <p>独立消费组（RocketMQ 同组订阅必须一致，不复用其他域消费组）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.SOFTWARE_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_SOFTWARE_EVENT,
        selectorExpression = RocketMQConfig.SOFTWARE_TAG_ALL
)
public class SoftwareEventConsumer implements RocketMQListener<Map<String, Object>> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            log.warn("[软件事件] 缺少 eventId，拒绝消费");
            return;
        }
        String eventType = (String) message.get("eventType");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        if (eventType == null || payload == null) {
            log.warn("[软件事件] 缺少 eventType/payload，eventId={}", eventId);
            return;
        }
        switch (eventType) {
            case "SOFTWARE_REVIEW_APPROVED" -> notifyReview(eventId, payload, true);
            case "SOFTWARE_REVIEW_REJECTED" -> notifyReview(eventId, payload, false);
            case "SOFTWARE_BANNED" -> notifyBanned(eventId, payload);
            case "SOFTWARE_COMMENT_VISIBLE" -> notifyComment(eventId, payload, false);
            case "SOFTWARE_REPLY_VISIBLE" -> notifyComment(eventId, payload, true);
            case "SOFTWARE_SOURCE_REPORTED" -> notifySourceReported(eventId, payload);
            default -> log.warn("[软件事件] 未知的软件事件类型，跳过 eventId={} type={}",
                    eventId, eventType);
        }
    }

    /** 投稿审核结果（作者）：载荷 softwareId/revisionId/authorId */
    private void notifyReview(String eventId, Map<String, Object> payload, boolean approved) {
        Long authorId = requireRecipient(payload, "authorId", eventId);
        if (authorId == null) {
            return;
        }
        Long softwareId = asLong(payload.get("softwareId"));
        if (approved) {
            boolean direct = Boolean.TRUE.equals(payload.get("directPublish"));
            notificationService.sendSoftwareEventNotification(authorId, eventId,
                    "SOFTWARE_REVIEW_APPROVED", direct ? "软件发布成功" : "软件审核通过",
                    direct ? "你的软件已直接发布，可在「我的软件」中查看与继续维护"
                            : "你的软件已通过审核并正式上线，可在「我的软件」中查看与继续维护",
                    softwareId, "SOFTWARE", null);
        } else {
            Object reasonObj = payload.get("reason");
            String reason = reasonObj == null ? "" : String.valueOf(reasonObj);
            notificationService.sendSoftwareEventNotification(authorId, eventId,
                    "SOFTWARE_REVIEW_REJECTED", "软件审核未通过",
                    "你的软件暂未通过审核" + (reason.isBlank() ? "" : "，理由：" + reason)
                            + "；修改后可重新提交",
                    softwareId, "SOFTWARE", null);
        }
    }

    /** 软件封禁（作者）：举报处置显式动作触发 */
    private void notifyBanned(String eventId, Map<String, Object> payload) {
        Long authorId = requireRecipient(payload, "authorId", eventId);
        if (authorId == null) {
            return;
        }
        Long softwareId = asLong(payload.get("softwareId"));
        Object reasonObj = payload.get("reason");
        String reason = reasonObj == null ? "" : String.valueOf(reasonObj);
        notificationService.sendSoftwareEventNotification(authorId, eventId,
                "SOFTWARE_BANNED", "软件已被平台封禁",
                "你的软件因违规被平台封禁" + (reason.isBlank() ? "" : "，理由：" + reason)
                        + "；如有疑问请联系管理员",
                softwareId, "SOFTWARE", null);
    }

    /** 评论/回复可见（软件作者或被回复人）：载荷 softwareId/commentId/recipientId/authorId */
    private void notifyComment(String eventId, Map<String, Object> payload, boolean reply) {
        Long recipientId = requireRecipient(payload, "recipientId", eventId);
        if (recipientId == null) {
            return;
        }
        Long softwareId = asLong(payload.get("softwareId"));
        Long commentId = asLong(payload.get("commentId"));
        Long authorId = asLong(payload.get("authorId"));
        if (reply) {
            notificationService.sendSoftwareEventNotification(recipientId, eventId,
                    "SOFTWARE_REPLY_VISIBLE", "收到新回复",
                    "你在小答软件的留言收到了新回复，点击查看",
                    softwareId, "SOFTWARE_COMMENT", null);
        } else {
            notificationService.sendSoftwareEventNotification(recipientId, eventId,
                    "SOFTWARE_COMMENT_VISIBLE", "软件收到新评论",
                    "你分享的软件收到了新评论，点击查看",
                    softwareId, "SOFTWARE_COMMENT", null);
        }
        log.debug("[软件事件] 评论可见通知 recipientId={} commentId={} authorId={}",
                recipientId, commentId, authorId);
    }

    /** 来源疑似失效（作者）：载荷 softwareId/sourceKey/distinctReporters */
    private void notifySourceReported(String eventId, Map<String, Object> payload) {
        Long authorId = requireRecipient(payload, "authorId", eventId);
        if (authorId == null) {
            return;
        }
        Long softwareId = asLong(payload.get("softwareId"));
        Object reportersObj = payload.get("distinctReporters");
        String reporters = reportersObj == null ? "多个" : String.valueOf(reportersObj);
        notificationService.sendSoftwareEventNotification(authorId, eventId,
                "SOFTWARE_SOURCE_REPORTED", "下载来源疑似失效",
                "你分享的软件有 " + reporters + " 位用户反馈下载来源可能失效，"
                        + "请检查并更新来源；核实结论以管理员人工确认为准",
                softwareId, "SOFTWARE_SOURCE", null);
    }

    /** recipientId 必须存在（无收件人不产生通知） */
    private Long requireRecipient(Map<String, Object> payload, String key, String eventId) {
        Long recipient = asLong(payload.get(key));
        if (recipient == null) {
            log.warn("[软件事件] 载荷缺少 {}，跳过通知 eventId={}", key, eventId);
        }
        return recipient;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && text.matches("\\d+")) {
            return Long.parseLong(text);
        }
        return null;
    }
}
