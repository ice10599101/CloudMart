package com.cloudmart.community.mq;

import com.cloudmart.community.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void publishLikeEvent(Long targetUserId, Long operatorUserId, Long postId, String postTitle) {
        CommunityEventMessage message = new CommunityEventMessage(
                "LIKE", targetUserId, operatorUserId, postId, "POST", postTitle, null
        );
        send(message);
    }

    public void publishCommentEvent(Long targetUserId, Long operatorUserId, Long postId, String postTitle, String commentPreview) {
        CommunityEventMessage message = new CommunityEventMessage(
                "COMMENT", targetUserId, operatorUserId, postId, "POST", postTitle, commentPreview
        );
        send(message);
    }

    public void publishCollectEvent(Long targetUserId, Long operatorUserId, Long postId, String postTitle) {
        CommunityEventMessage message = new CommunityEventMessage(
                "COLLECT", targetUserId, operatorUserId, postId, "POST", postTitle, null
        );
        send(message);
    }

    public void publishFollowEvent(Long targetUserId, Long operatorUserId) {
        CommunityEventMessage message = new CommunityEventMessage(
                "FOLLOW", targetUserId, operatorUserId, null, "USER", null, null
        );
        send(message);
    }

    public void publishShareEvent(Long targetUserId, Long operatorUserId, Long postId, String postTitle, String channel) {
        CommunityEventMessage message = new CommunityEventMessage(
                "SHARE", targetUserId, operatorUserId, postId, "POST", postTitle, channel
        );
        send(message);
    }

    public void publishMentionEvent(Long targetUserId, Long operatorUserId, Long postId, String postTitle) {
        CommunityEventMessage message = new CommunityEventMessage(
                "MENTION", targetUserId, operatorUserId, postId, "POST", postTitle, null
        );
        send(message);
    }

    public void publishTagNewPostEvent(Long targetUserId, Long operatorUserId, Long postId, String postTitle, String tagName) {
        CommunityEventMessage message = new CommunityEventMessage(
                "TAG_NEW_POST", targetUserId, operatorUserId, postId, "TAG", postTitle, tagName
        );
        send(message);
    }

    /**
     * 批量发送点赞数变更消息，由定时任务调用。
     * 消费者接收后异步更新 posts 表的 like_count 字段。
     *
     * @param messages 点赞数变更消息列表
     */
    public void publishLikeTimesBatch(java.util.List<LikeTimesMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            String destination = RocketMQConfig.COMMUNITY_TOPIC + ":" + RocketMQConfig.COMMUNITY_TAG_LIKE_TIMES;
            for (LikeTimesMessage message : messages) {
                rocketMQTemplate.syncSend(destination, message);
            }
            log.info("Like-times batch sent: count={}", messages.size());
        } catch (Exception e) {
            log.error("Failed to send like-times batch: count={}", messages.size(), e);
        }
    }

    private void send(CommunityEventMessage message) {
        try {
            String destination = RocketMQConfig.COMMUNITY_TOPIC + ":" + RocketMQConfig.COMMUNITY_TAG_EVENT;
            rocketMQTemplate.syncSend(destination, message);
            log.info("Community event sent: type={}, targetUserId={}", message.type(), message.targetUserId());
        } catch (Exception e) {
            log.error("Failed to send community event: type={}", message.type(), e);
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
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * 点赞数变更消息。
     *
     * @param targetType 目标类型（POST / COMMENT）
     * @param targetId   目标ID
     * @param delta      点赞数增量（正数为点赞，负数为取消）
     */
    public record LikeTimesMessage(
            String targetType,
            Long targetId,
            int delta
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
