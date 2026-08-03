package com.cloudmart.community.mq;

import com.cloudmart.community.config.RocketMQConfig;
import com.cloudmart.community.mq.CommunityEventProducer.LikeTimesMessage;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 点赞数变更消费者：接收 MQ 消息，原子更新目标表的 like_count 字段。
 *
 * <p>支持的目标类型：
 * <ul>
 *   <li>POST — 更新 posts 表</li>
 *   <li>COMMENT — 更新 post_comments 表</li>
 * </ul>
 *
 * <p>使用 {@code GREATEST(0, like_count + delta)} 防止负数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.COMMUNITY_TOPIC,
        consumerGroup = RocketMQConfig.CG_COMMUNITY_LIKE_TIMES,
        selectorExpression = RocketMQConfig.COMMUNITY_TAG_LIKE_TIMES
)
public class LikeTimesConsumer implements RocketMQListener<LikeTimesMessage> {

    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;

    @Override
    public void onMessage(LikeTimesMessage message) {
        try {
            int updated = switch (message.targetType()) {
                case "POST" -> postMapper.updateLikeCount(message.targetId(), message.delta());
                case "COMMENT" -> postCommentMapper.updateLikeCount(message.targetId(), message.delta());
                default -> {
                    log.warn("Unsupported targetType for like-times sync: {}", message.targetType());
                    yield 0;
                }
            };
            if (updated == 0) {
                log.warn("Like-times update affected 0 rows: targetType={}, targetId={}, delta={}",
                        message.targetType(), message.targetId(), message.delta());
            } else {
                log.debug("Like-times updated: targetType={}, targetId={}, delta={}",
                        message.targetType(), message.targetId(), message.delta());
            }
        } catch (Exception e) {
            log.error("Failed to update like-times: targetType={}, targetId={}, delta={}",
                    message.targetType(), message.targetId(), message.delta(), e);
            throw new RuntimeException(e);
        }
    }
}
