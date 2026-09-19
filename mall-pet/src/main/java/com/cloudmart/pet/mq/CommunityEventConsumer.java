package com.cloudmart.pet.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetContextCounter;
import com.cloudmart.pet.repository.PetContextCounterMapper;
import com.cloudmart.pet.service.impl.PetStateService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 社区事件消费者（mall-pet 自建消费组，独立于 mall-notification 的消费组）。
 *
 * <p>消费 {@code community-events: event}（LIKE/COMMENT/COLLECT/FOLLOW...），做两件事：</p>
 * <ol>
 *   <li>维护宠物 AI 上下文计数器（pet_context_counter）——用户打开宠物页触发
 *       主动消息评估时聚合成一条社区播报并清零（原文档 §31 通知聚合），
 *       本消费者只累加计数，不重复落通知（通知落库由 mall-notification 完成）。</li>
 *   <li><b>社区行为影响宠物成长</b>（原文档 §1.1）：给主宠加经验，每日上限
 *       {@code pet.community-growth.daily-exp-cap}（Redis 计数，Fail-Open 时不再叠加）。</li>
 * </ol>
 *
 * <p>幂等：消费假设消息可能重复（at-least-once），重复投递会导致计数/经验多加——
 * 计数仅用于播报文案量级展示（"收到了 N 个赞"），经验有每日上限封顶，
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

    /** Redis Key：社区行为成长经验日计数（每日 UTC 上限） */
    static final String KEY_COMMUNITY_EXP = "pet:growth:community:%d:%s";

    private final PetContextCounterMapper counterMapper;
    private final PetStateService stateService;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;

    public CommunityEventConsumer(PetContextCounterMapper counterMapper,
                                  PetStateService stateService,
                                  PetProperties properties,
                                  StringRedisTemplate redisTemplate) {
        this.counterMapper = counterMapper;
        this.stateService = stateService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
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
            grantCommunityGrowth(message.targetUserId());
        } catch (Exception e) {
            log.error("社区事件处理失败（不阻断消费队列）: type={}, targetUserId={}",
                    message.type(), message.targetUserId(), e);
        }
    }

    /**
     * 社区行为影响宠物成长：给主宠加经验，日上限用 Redis 计数封顶（每天首次写入设置 24h TTL）。
     * 无宠物 / Redis 故障 / 宠物库异常均不阻断消费（成长是激励型附加收益）。
     */
    private void grantCommunityGrowth(Long userId) {
        try {
            PetProperties.CommunityGrowth cfg = properties.getCommunityGrowth();
            int expPerEvent = cfg.getExpPerEvent();
            if (expPerEvent <= 0) {
                return;
            }
            String key = String.format(KEY_COMMUNITY_EXP, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key, expPerEvent);
            if (used != null && used == expPerEvent) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            if (used != null && used > cfg.getDailyExpCap()) {
                return;
            }
            Pet pet = stateService.findByUserId(userId);
            if (pet == null) {
                return;
            }
            stateService.grantExp(pet, expPerEvent);
        } catch (Exception e) {
            log.warn("社区行为成长结算失败（不阻断消费）: userId={}", userId, e);
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
