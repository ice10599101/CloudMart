package com.cloudmart.pet.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.PetContextCounter;
import com.cloudmart.pet.repository.PetContextCounterMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 社区事件消费者（mall-pet 自建消费组，独立于 mall-notification 的消费组）。
 *
 * <p>消费 {@code community-events: event}（LIKE/COMMENT/COLLECT/FOLLOW...），
 * 维护宠物 AI 上下文计数器（pet_context_counter）。用户打开宠物页触发
 * 主动消息评估时聚合成一条社区播报并清零（原文档 §31 通知聚合）——
 * 本消费者只累加计数，不重复落通知（通知落库由 mall-notification 完成）。</p>
 *
 * <p>幂等：消费假设消息可能重复（at-least-once），重复投递会导致计数多加——
 * 计数器仅用于播报文案量级展示（"收到了 N 个赞"），轻微误差可接受，
 * 与 mall-notification 社区消费者采用同一容错口径。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = RocketMQConfig.COMMUNITY_TOPIC,
        consumerGroup = RocketMQConfig.CG_PET_COMMUNITY_EVENT,
        selectorExpression = RocketMQConfig.COMMUNITY_TAG_EVENT
)
public class CommunityEventConsumer implements RocketMQListener<CommunityEventConsumer.CommunityEventMessage> {

    private final PetContextCounterMapper counterMapper;

    public CommunityEventConsumer(PetContextCounterMapper counterMapper) {
        this.counterMapper = counterMapper;
    }

    @Override
    public void onMessage(CommunityEventMessage message) {
        try {
            if (message.targetUserId() == null || message.type() == null) {
                return;
            }
            int deltaComments = "COMMENT".equalsIgnoreCase(message.type()) ? 1 : 0;
            int deltaLikes = "LIKE".equalsIgnoreCase(message.type()) ? 1 : 0;
            int deltaFollows = "FOLLOW".equalsIgnoreCase(message.type()) ? 1 : 0;
            int deltaCollects = "COLLECT".equalsIgnoreCase(message.type()) ? 1 : 0;
            if (deltaComments == 0 && deltaLikes == 0 && deltaFollows == 0 && deltaCollects == 0) {
                return;
            }
            upsertCounter(message.targetUserId(), deltaComments, deltaLikes, deltaFollows, deltaCollects);
        } catch (Exception e) {
            log.error("社区事件计数失败（不阻断消费队列）: type={}, targetUserId={}",
                    message.type(), message.targetUserId(), e);
        }
    }

    private void upsertCounter(Long userId, int comments, int likes, int follows, int collects) {
        PetContextCounter existing = counterMapper.selectOne(new LambdaQueryWrapper<PetContextCounter>()
                .eq(PetContextCounter::getUserId, userId));
        if (existing == null) {
            PetContextCounter counter = new PetContextCounter();
            counter.setUserId(userId);
            counter.setNewComments(comments);
            counter.setNewLikes(likes);
            counter.setNewFollows(follows);
            counter.setNewCollects(collects);
            try {
                counterMapper.insert(counter);
            } catch (DuplicateKeyException e) {
                // 并发首条消息：降级为更新路径
                incrementExisting(userId, comments, likes, follows, collects);
            }
            return;
        }
        incrementExisting(userId, comments, likes, follows, collects);
    }

    private void incrementExisting(Long userId, int comments, int likes, int follows, int collects) {
        PetContextCounter patch = counterMapper.selectOne(new LambdaQueryWrapper<PetContextCounter>()
                .eq(PetContextCounter::getUserId, userId));
        if (patch == null) {
            return;
        }
        patch.setNewComments(patch.getNewComments() + comments);
        patch.setNewLikes(patch.getNewLikes() + likes);
        patch.setNewFollows(patch.getNewFollows() + follows);
        patch.setNewCollects(patch.getNewCollects() + collects);
        counterMapper.updateById(patch);
    }

    /** 社区事件消息（与 mall-community CommunityEventProducer 字段对齐） */
    public record CommunityEventMessage(
            String type,
            Long targetUserId,
            Long operatorUserId,
            Long bizId,
            String bizType,
            String bizTitle,
            String extra
    ) implements Serializable {
    }
}
