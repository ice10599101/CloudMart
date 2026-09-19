package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.feign.NotificationFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.service.PetReminderService;
import com.cloudmart.pet.vo.PetReminderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 宠物提醒/主动消息实现。
 *
 * <p>频控（原文档 §32：避免变成骚扰）：每日主动消息 ≤ {@code proactive.dailyLimit}（Redis 日计数）
 * + 最小间隔 {@code minIntervalSeconds}；触发器全部惰性评估（打开宠物页时），
 * 不做后台定时轰炸。每个触发点当日幂等（Redis 日标记）。</p>
 *
 * <p>Redis 故障策略（Fail-Open）：频控计数异常时跳过主动消息（宁缺勿扰），
 * 不阻断宠物页主流程。</p>
 */
@Service
@Slf4j
public class PetReminderServiceImpl implements PetReminderService {

    private static final String KEY_PROACTIVE_DAILY = "pet:ratelimit:proactive:%d:%s";
    private static final String KEY_PROACTIVE_INTERVAL = "pet:proactive:interval:%d";
    private static final String KEY_TRIGGER_FLAG = "pet:proactive:sent:%d:%s:%s";

    private final NotificationFeignClient notificationFeignClient;
    private final PetActivityMapper activityMapper;
    private final PetContextService contextService;
    private final PetEventProducer eventProducer;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;

    public PetReminderServiceImpl(NotificationFeignClient notificationFeignClient,
                                  PetActivityMapper activityMapper,
                                  PetContextService contextService,
                                  PetEventProducer eventProducer,
                                  PetProperties properties,
                                  StringRedisTemplate redisTemplate) {
        this.notificationFeignClient = notificationFeignClient;
        this.activityMapper = activityMapper;
        this.contextService = contextService;
        this.eventProducer = eventProducer;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<PetReminderVO> listReminders(Long userId) {
        // 复用现有通知系统（type=PET，子类型在 bizType）；Feign 降级 Fail-Open 空列表
        List<NotificationFeignClient.NotificationItemVO> items =
                notificationFeignClient.listNotifications(userId, "PET", 1, 20).data();
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new PetReminderVO(item.id(), item.bizType(), item.title(),
                        item.content(), item.bizId(), item.isRead(), parseTime(item.createdAt())))
                .toList();
    }

    @Override
    public void evaluateOnVisit(Long userId, Pet pet) {
        if (!tryAcquireInterval(userId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String today = LocalDate.now(ZoneId.of("UTC")).toString();

        // 1. 捞瓶完成待领取（业务级幂等：同一活动只提醒一次）
        PetActivity bottle = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.BOTTLE_FISHING.name())
                .eq(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (bottle != null && tryAcquireTrigger(userId, today, "BOTTLE_READY", bottle.getId())) {
            publish(userId, "PET_BOTTLE_CAUGHT",
                    "主人！我捞到漂流瓶啦！",
                    pet.getName() + "：" + "我回来啦！快去看看我捞到了什么好东西～", bottle.getId());
        }

        // 2. 社区动态聚合播报（评论/点赞/关注合并成一条，原文档 §31）
        PetContextService.PetContext context = contextService.buildContext(userId, pet);
        int total = context.newComments() + context.newLikes() + context.newFollows() + context.newCollects();
        if (total > 0 && tryAcquireTrigger(userId, today, "COMMUNITY_DIGEST", 0L)) {
            publish(userId, "PET_COMMUNITY_DIGEST",
                    "主人在社区好受欢迎呀！",
                    pet.getName() + "：" + "主人主人！你的帖子收到了 " + context.newLikes() + " 个赞、"
                            + context.newComments() + " 条评论，还有 " + context.newFollows()
                            + " 位新朋友关注了你～", null);
            contextService.resetCounters(userId);
        }

        // 3. 饿了（每日一次）
        if (pet.getHunger() < properties.getInteraction().getHungryRemindThreshold()
                && tryAcquireTrigger(userId, today, "PET_HUNGRY", 0L)) {
            publish(userId, "PET_HUNGRY",
                    "我的肚子咕咕叫啦…",
                    pet.getName() + "：" + "主人，我的肚子有点饿…可以喂喂我吗？", pet.getId());
        }

        // 4. 每日问候（每日一次）
        if (tryAcquireTrigger(userId, today, "DAILY_GREETING", 0L)) {
            publish(userId, "PET_DAILY_GREETING",
                    dailyGreetingTitle(now),
                    pet.getName() + "：" + dailyGreetingText(now, pet), pet.getId());
        }
    }

    private void publish(Long userId, String reminderType, String title, String content, Long bizId) {
        if (!tryConsumeDailyQuota(userId)) {
            return;
        }
        eventProducer.publish(RocketMQConfig.PET_TAG_PROACTIVE, new PetEventProducer.PetEventMessage(
                userId, reminderType, title, content, bizId, reminderType));
    }

    /** 最小间隔（默认 60s）：SETNX 抢占，防止刷新页面连发 */
    private boolean tryAcquireInterval(Long userId) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    String.format(KEY_PROACTIVE_INTERVAL, userId), "1",
                    Duration.ofSeconds(properties.getProactive().getMinIntervalSeconds()));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("主动消息间隔频控 Redis 故障（Fail-Open 跳过本次评估）: userId={}", userId, e);
            return false;
        }
    }

    /** 触发点当日幂等标记 */
    private boolean tryAcquireTrigger(Long userId, String today, String trigger, Long bizId) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    String.format(KEY_TRIGGER_FLAG, userId, today, trigger) + ":" + bizId, "1",
                    Duration.ofHours(24));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            return false;
        }
    }

    /** 每日主动消息总量 ≤3（原文档 §32） */
    private boolean tryConsumeDailyQuota(Long userId) {
        try {
            String key = String.format(KEY_PROACTIVE_DAILY, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            return used != null && used <= properties.getProactive().getDailyLimit();
        } catch (Exception e) {
            return false;
        }
    }

    private String dailyGreetingTitle(LocalDateTime now) {
        int hour = now.getHour();
        if (hour < 11) {
            return "早安主人～";
        }
        if (hour < 18) {
            return "主人下午好呀～";
        }
        return "今天辛苦啦，主人～";
    }

    private String dailyGreetingText(LocalDateTime now, Pet pet) {
        int hour = now.getHour();
        if (hour < 11) {
            return "早安！今天也要一起开心哦，我先去晒个太阳～";
        }
        if (hour < 18) {
            return "下午好呀！要不要带我去打工或者读书呀？";
        }
        return "今天辛苦啦，要不要陪我玩一会再休息？";
    }

    private LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
