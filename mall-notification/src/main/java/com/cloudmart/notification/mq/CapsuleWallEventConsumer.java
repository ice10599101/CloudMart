package com.cloudmart.notification.mq;

import com.cloudmart.common.util.Ids;
import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * R17 公共胶囊墙裁决结果消费者。
 *
 * <p>生产端 mall-wish 在裁决/下架同事务经 Outbox 中继投递
 * （CAPSULE_WALL_DECIDED，tag=capsule-wall）。消费端按 eventId 唯一去重
 * （notifications.uk_notification_event）——重复投递不产生重复站内信；
 * 失败重抛触发 MQ 重试。作者自助撤回/撤下不产生事件（无通知必要）。</p>
 *
 * <p>独立消费组：与 {@link WishEventConsumer}（selector=capsule-available）
 * 订阅关系不同，RocketMQ 同组各实例订阅必须一致，故不可复用其消费组。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.WISH_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_WISH_CAPSULE_WALL,
        selectorExpression = RocketMQConfig.WISH_TAG_CAPSULE_WALL
)
public class CapsuleWallEventConsumer implements RocketMQListener<Map<String, Object>> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            log.warn("[R17] 胶囊墙裁决事件缺少 eventId，拒绝消费");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        if (payload == null) {
            log.warn("[R17] 胶囊墙裁决事件缺少 payload，eventId={}", eventId);
            return;
        }
        // BASE-01：字符串/数字形态均接受（防字符串形态被静默丢弃）
        Long userId = Ids.parse(payload.get("userId"));
        Long capsuleId = Ids.parse(payload.get("capsuleId"));
        if (userId == null || capsuleId == null) {
            log.warn("[R17] 胶囊墙裁决事件缺少 userId/capsuleId，eventId={}", eventId);
            return;
        }
        Object titleObj = payload.get("title");
        String title = titleObj == null ? "时间胶囊" : String.valueOf(titleObj);
        Object resultObj = payload.get("result");
        String result = resultObj == null ? "" : String.valueOf(resultObj);
        Object reasonObj = payload.get("reason");
        String reason = reasonObj == null ? "" : String.valueOf(reasonObj);

        String type;
        String noticeTitle;
        String content;
        switch (result) {
            case "APPROVED" -> {
                type = "CAPSULE_WALL_APPROVED";
                noticeTitle = "胶囊上墙啦";
                content = "你的胶囊《" + title + "》已通过审核，正在公共胶囊墙匿名展出";
            }
            case "REJECTED" -> {
                type = "CAPSULE_WALL_REJECTED";
                noticeTitle = "胶囊未通过审核";
                content = "你的胶囊《" + title + "》暂未通过公共墙审核"
                        + (reason.isBlank() ? "" : "，理由：" + reason)
                        + "；修改内容后可以重新申请";
            }
            case "TAKEN_DOWN" -> {
                type = "CAPSULE_WALL_TAKEN_DOWN";
                noticeTitle = "胶囊已被下架";
                content = "你的胶囊《" + title + "》已被管理员从公共墙下架"
                        + (reason.isBlank() ? "" : "，理由：" + reason)
                        + "；如有疑问可联系客服";
            }
            default -> {
                log.warn("[R17] 未知胶囊墙裁决结果 result={}, eventId={}", result, eventId);
                return;
            }
        }

        notificationService.sendWishEventNotification(
                userId, eventId, type, noticeTitle, content, capsuleId, "CAPSULE");
        log.info("胶囊墙裁决通知已发送: capsuleId={}, userId={}, result={}, eventId={}",
                capsuleId, userId, result, eventId);
    }
}
