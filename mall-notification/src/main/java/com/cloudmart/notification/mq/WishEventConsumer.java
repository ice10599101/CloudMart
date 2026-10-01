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
 * 心愿域事件消费者（Sprint 2.4：时间胶囊到期待开启推送）。
 *
 * <p>生产端（mall-wish CapsuleEventProducer）仅在 SEALED→AVAILABLE CAS
 * 流转成功后发送，每胶囊至多一条（扫描幂等）；消费端假设消息可能重复投递
 * （RocketMQ at-least-once），通知落库以业务去重为准——推送记录允许
 * 重复展示，不产生用户侧副作用（站内信多条可见属可接受降级，
 * 管理端推送记录按 capsuleId 可核对）。</p>
 */
/**
 * 心愿域事件消费者（W05：时间胶囊到期待开启推送）。
 *
 * <p>生产端经 WishOutbox 中继投递（eventId 注入 payload，退避重试直至成功/DEAD）。
 * 消费端：eventId 唯一去重（notifications.uk_notification_event）——重复投递不产生
 * 重复站内通知；失败重抛触发 MQ 重试（原 catch 吞异常缺陷修复）。</p>
 */
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.WISH_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_WISH_EVENT,
        selectorExpression = RocketMQConfig.WISH_TAG_CAPSULE_AVAILABLE
)
public class WishEventConsumer implements RocketMQListener<Map<String, Object>> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            log.warn("[W05] 胶囊事件缺少 eventId，拒绝消费");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        if (payload == null) {
            payload = message;
        }
        Long capsuleId = ((Number) payload.get("capsuleId")).longValue();
        Long userId = ((Number) payload.get("userId")).longValue();
        Object titleObj = payload.get("title");
        String title = titleObj == null ? null : String.valueOf(titleObj);

        String content = "你封存的《" + (title != null ? title : "时间胶囊") + "》已到开启时间，来拆开这份过去的礼物吧";
        notificationService.sendWishEventNotification(
                userId, eventId, "CAPSULE_AVAILABLE", "时间胶囊到期啦", content, capsuleId, "CAPSULE");
        log.info("Capsule available notification sent: capsuleId={}, userId={}, eventId={}",
                capsuleId, userId, eventId);
    }
}
