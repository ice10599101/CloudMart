package com.cloudmart.notification.mq;

import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.COMMUNITY_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_COMMUNITY_EVENT,
        selectorExpression = RocketMQConfig.COMMUNITY_TAG_EVENT
)
public class CommunityEventConsumer implements RocketMQListener<CommunityEventConsumer.CommunityEventMessage> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(CommunityEventMessage message) {
        try {
            String title = switch (message.type()) {
                case "LIKE" -> "收到点赞";
                case "COMMENT" -> "收到评论";
                case "COLLECT" -> "内容被收藏";
                case "FOLLOW" -> "新增粉丝";
                case "SHARE" -> "内容被分享";
                case "MENTION" -> "有人@了你";
                case "TAG_NEW_POST" -> "话题新帖";
                default -> "社区互动";
            };

            String content = switch (message.type()) {
                case "LIKE" -> "有人赞了你的帖子《" + message.bizTitle() + "》";
                case "COMMENT" -> "有人评论了你的帖子《" + message.bizTitle() + "》：" + (message.extra() != null && message.extra().length() > 50 ? message.extra().substring(0, 50) + "..." : message.extra());
                case "COLLECT" -> "有人收藏了你的帖子《" + message.bizTitle() + "》";
                case "FOLLOW" -> "有人关注了你";
                case "SHARE" -> "有人分享了你的帖子《" + message.bizTitle() + "》";
                case "MENTION" -> "有人在帖子《" + message.bizTitle() + "》中@了你";
                case "TAG_NEW_POST" -> "你关注的话题#" + (message.extra() != null ? message.extra() : "") + "有新帖子《" + message.bizTitle() + "》";
                default -> "你有新的社区互动";
            };

            Long bizId = message.bizId() != null ? message.bizId() : message.operatorUserId();
            String bizType = message.bizType() != null ? message.bizType() : "USER";

            notificationService.sendNotificationToUser(
                    message.targetUserId(), message.type(), title, content, bizId, bizType
            );
            log.info("Community notification sent: type={}, targetUserId={}", message.type(), message.targetUserId());
        } catch (Exception e) {
            log.error("Failed to send community notification: type={}", message.type(), e);
        }
    }

    public record CommunityEventMessage(
            String type,
            Long targetUserId,
            Long operatorUserId,
            Long bizId,
            String bizType,
            String bizTitle,
            String extra
    ) implements Serializable {}
}
