package com.cloudmart.notification.mq;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.feign.UserFeignClient;
import com.cloudmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

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
    private final UserFeignClient userFeignClient;

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

            // 操作者昵称：让用户知道具体是谁在互动；mall-user 不可用时 Fail-Open 降级为「有人」
            String actor = resolveActorNickname(message.operatorUserId());
            String actorPrefix = actor != null ? actor + " " : "有人";
            String content = switch (message.type()) {
                case "LIKE" -> actorPrefix + "赞了你的帖子《" + message.bizTitle() + "》";
                case "COMMENT" -> actorPrefix + "评论了你的帖子《" + message.bizTitle() + "》：" + (message.extra() != null && message.extra().length() > 50 ? message.extra().substring(0, 50) + "..." : message.extra());
                case "COLLECT" -> actorPrefix + "收藏了你的帖子《" + message.bizTitle() + "》";
                case "FOLLOW" -> actorPrefix + "关注了你";
                case "SHARE" -> actorPrefix + "分享了你的帖子《" + message.bizTitle() + "》";
                case "MENTION" -> actorPrefix + "在帖子《" + message.bizTitle() + "》中@了你";
                case "TAG_NEW_POST" -> "你关注的话题#" + (message.extra() != null ? message.extra() : "") + "有新帖子《" + message.bizTitle() + "》";
                default -> "你有新的社区互动";
            };

            Long bizId = message.bizId() != null ? message.bizId() : message.operatorUserId();
            String bizType = message.bizType() != null ? message.bizType() : "USER";

            notificationService.sendNotificationToUser(
                    message.targetUserId(), message.type(), title, content, bizId, bizType
            );
            log.info("Community notification sent: type={}, targetUserId={}, actor={}",
                    message.type(), message.targetUserId(), actor);
        } catch (Exception e) {
            log.error("Failed to send community notification: type={}", message.type(), e);
        }
    }

    /** 批量接口取单个用户昵称；服务降级/字段缺失返回 null（调用方退回「有人」语义） */
    private String resolveActorNickname(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            ApiResponse<List<Map<String, Object>>> response = userFeignClient.batchGetUsers(List.of(userId));
            if (response.success() && response.data() != null && !response.data().isEmpty()) {
                Object nickname = response.data().get(0).get("nickname");
                if (nickname != null && !nickname.toString().isBlank()) {
                    return nickname.toString();
                }
            }
        } catch (Exception e) {
            log.warn("通知操作者昵称解析失败，降级为「有人」: userId={}, {}", userId, e.getMessage());
        }
        return null;
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
