package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.feign.CommunityActivityFeignClient;
import com.cloudmart.pet.feign.NotificationFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.service.PetEventService;
import com.cloudmart.pet.service.PetReminderService;
import com.cloudmart.pet.vo.PetEventVO;
import com.cloudmart.pet.vo.PetReminderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 宠物提醒/主动消息实现（原文档 §27-33）。
 *
 * <p>触发器（惰性评估，打开宠物页时）：DAILY_GREETING / LONG_ABSENT / PET_HUNGRY /
 * BOTTLE_READY / MESSAGE(私信) / ACTIVITY_ENDING(活动) / COMMUNITY_DIGEST（聚合清零）。
 * 频控：每日 ≤ proactive.dailyLimit（Redis 日计数）+ 最小间隔；每个触发点当日幂等。</p>
 *
 * <p>Redis 故障策略（Fail-Open）：频控/标记异常时跳过主动消息（宁缺勿扰），
 * 不阻断宠物页主流程。私信/活动数据经 Feign，降级 Fail-Open 跳过。</p>
 */
@Service
@Slf4j
public class PetReminderServiceImpl implements PetReminderService {

    private static final String KEY_PROACTIVE_DAILY = "pet:ratelimit:proactive:%d:%s";
    private static final String KEY_PROACTIVE_INTERVAL = "pet:proactive:interval:%d";
    private static final String KEY_TRIGGER_FLAG = "pet:proactive:sent:%d:%s:%s";
    private static final String KEY_LAST_VISIT = "pet:visit:last:%d";
    /** 长时间未陪伴判定阈值（原文档 §33 LONG_TIME_ABSENT） */
    private static final Duration ABSENT_THRESHOLD = Duration.ofHours(24);
    /** 活动即将结束提醒窗口（原文档 §28.6：还有 2 小时） */
    private static final Duration ACTIVITY_ENDING_WINDOW = Duration.ofHours(2);

    /** 提醒优先级映射（原文档 §30）：P0 重要 / P1 普通 / P2 低 */
    private static final List<String> P0_TYPES = List.of("PET_MESSAGE", "PET_ACTIVITY_ENDING");
    private static final List<String> P1_TYPES = List.of("PET_BOTTLE_CAUGHT", "PET_WORK_COMPLETED",
            "PET_STUDY_COMPLETED", "PET_BATTLE_FINISHED", "PET_BATTLE_CHALLENGE", "PET_COMMUNITY_DIGEST",
            // 二期：进化（成长里程碑）/ 串门（社区互动）/ 活动达成（§89 社区宠物活动）
            "PET_EVOLVED", "PET_VISIT", "PET_EVENT_READY");

    private final NotificationFeignClient notificationFeignClient;
    private final CommunityActivityFeignClient activityFeignClient;
    private final PetActivityMapper activityMapper;
    private final PetContextService contextService;
    private final PetEventProducer eventProducer;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final PetEventService eventService;

    public PetReminderServiceImpl(NotificationFeignClient notificationFeignClient,
                                  CommunityActivityFeignClient activityFeignClient,
                                  PetActivityMapper activityMapper,
                                  PetContextService contextService,
                                  PetEventProducer eventProducer,
                                  PetProperties properties,
                                  StringRedisTemplate redisTemplate,
                                  PetEventService eventService) {
        this.notificationFeignClient = notificationFeignClient;
        this.activityFeignClient = activityFeignClient;
        this.activityMapper = activityMapper;
        this.contextService = contextService;
        this.eventProducer = eventProducer;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.eventService = eventService;
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
                        item.content(), item.bizId(), item.isRead(), parseTime(item.createdAt()),
                        priorityOf(item.bizType())))
                .toList();
    }

    @Override
    public long unreadCount(Long userId) {
        try {
            Long count = notificationFeignClient.getUnreadCount(userId, "PET").data();
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("宠物提醒未读数查询降级（Fail-Open）: userId={}", userId, e);
            return 0L;
        }
    }

    /** 优先级映射（原文档 §30）：P0 私信/活动到期；P1 评论/捞瓶/任务/对战；P2 其余 */
    static String priorityOf(String reminderType) {
        if (reminderType != null && P0_TYPES.contains(reminderType)) {
            return "P0";
        }
        if (reminderType != null && P1_TYPES.contains(reminderType)) {
            return "P1";
        }
        return "P2";
    }

    @Override
    public void evaluateOnVisit(Long userId, Pet pet) {
        if (!tryAcquireInterval(userId)) {
            return;
        }
        // 配额先于"触发点当日标记"判断：否则配额耗尽时各触发点的当日幂等标记已被消耗，
        // 当天再也不会补发该提醒（§32 配额 - trigger 顺序缺陷）
        if (!hasDailyQuota(userId)) {
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

        // 2. 社区动态聚合播报（评论/点赞/关注/收藏合并成一条，原文档 §31）
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

        // 3. 私信提醒（原文档 §28.3；P0）
        long unreadChat = unreadChatCount(userId);
        if (unreadChat > 0 && tryAcquireTrigger(userId, today, "PET_MESSAGE", 0L)) {
            publish(userId, "PET_MESSAGE",
                    "主人，你有一条新的消息",
                    pet.getName() + "：" + "有 " + unreadChat + " 条私信还没看哦，快去消息页看看吧！", null);
        }

        // 4. 活动即将结束提醒（原文档 §28.6：社区活动还有 2 小时就结束；P0）
        remindEndingActivities(userId, pet, today);

        // 5. 长时间未陪伴（原文档 §33 LONG_TIME_ABSENT）：上次访问超过 24h
        if (isLongAbsent(userId) && tryAcquireTrigger(userId, today, "LONG_ABSENT", 0L)) {
            publish(userId, "PET_LONG_ABSENT",
                    "主人，你好久没来看我啦",
                    pet.getName() + "：" + "主人～我已经等了你一整天了，肚子和心情都需要你照顾呀！", pet.getId());
        }

        // 6. 社区宠物活动达成可领奖（原文档 §89；每日一次）
        remindPetEvents(userId, pet, today);

        // 7. 饿了（每日一次）
        if (pet.getHunger() < properties.getInteraction().getHungryRemindThreshold()
                && tryAcquireTrigger(userId, today, "PET_HUNGRY", 0L)) {
            publish(userId, "PET_HUNGRY",
                    "我的肚子咕咕叫啦…",
                    pet.getName() + "：" + "主人，我的肚子有点饿…可以喂喂我吗？", pet.getId());
        }

        // 8. 每日问候（每日一次）
        if (tryAcquireTrigger(userId, today, "DAILY_GREETING", 0L)) {
            publish(userId, "PET_DAILY_GREETING",
                    dailyGreetingTitle(now),
                    pet.getName() + "：" + dailyGreetingText(now, pet), pet.getId());
        }

        markVisited(userId);
    }

    /** 社区宠物活动达成提醒（进度惰性统计后取第一个可领奖活动；Fail-Open 不阻断访问） */
    private void remindPetEvents(Long userId, Pet pet, String today) {
        try {
            List<PetEventVO> events = eventService.eventsForPet(pet);
            events.stream()
                    .filter(event -> Boolean.TRUE.equals(event.claimable()))
                    .findFirst()
                    .ifPresent(event -> {
                        if (tryAcquireTrigger(userId, today, "PET_EVENT_READY", 0L)) {
                            publish(userId, "PET_EVENT_READY",
                                    "活动达成啦！",
                                    pet.getName() + "：「" + event.name() + "」已经完成啦，快去领奖励吧！", null);
                        }
                    });
        } catch (Exception e) {
            log.warn("宠物活动提醒评估降级（Fail-Open）: userId={}", userId, e);
        }
    }

    /** 活动即将结束：ACTIVE 且 validTo 落在 2h 窗口内（Feign Fail-Open） */
    private void remindEndingActivities(Long userId, Pet pet, String today) {
        try {
            List<CommunityActivityFeignClient.CommunityActivityVO> activities =
                    activityFeignClient.listActivities().data();
            if (activities == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            for (CommunityActivityFeignClient.CommunityActivityVO activity : activities) {
                if (!"ACTIVE".equals(activity.status()) || activity.validTo() == null) {
                    continue;
                }
                LocalDateTime validTo;
                try {
                    validTo = LocalDateTime.parse(activity.validTo(), formatter);
                } catch (Exception e) {
                    continue;
                }
                Duration untilEnd = Duration.between(now, validTo);
                boolean endingSoon = !untilEnd.isNegative() && untilEnd.compareTo(ACTIVITY_ENDING_WINDOW) <= 0;
                if (endingSoon && tryAcquireTrigger(userId, today, "ACTIVITY_ENDING", activity.id())) {
                    long minutes = Math.max(1, untilEnd.toMinutes());
                    publish(userId, "PET_ACTIVITY_ENDING",
                            "社区活动还有 " + (minutes >= 60 ? (minutes / 60) + " 小时" : minutes + " 分钟") + "就结束啦",
                            pet.getName() + "：" + "「" + activity.title() + "」马上就要结束了，抓紧时间参加哦！", activity.id());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("活动提醒查询降级（Fail-Open）: userId={}", userId, e);
        }
    }

    /** 私信未读（Feign Fail-Open：降级视为 0 → 不提醒） */
    private long unreadChatCount(Long userId) {
        try {
            Long count = notificationFeignClient.getUnreadChatCount(userId).data();
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 上次访问超过 24h（Redis TTL 24h 的访问键过期即视为缺席；降级视为正常访问） */
    private boolean isLongAbsent(Long userId) {
        try {
            return Boolean.FALSE.equals(redisTemplate.hasKey(String.format(KEY_LAST_VISIT, userId)));
        } catch (Exception e) {
            return false;
        }
    }

    /** 记录本次访问（24h TTL；LONG_ABSENT 判定依据） */
    private void markVisited(Long userId) {
        try {
            redisTemplate.opsForValue().set(String.format(KEY_LAST_VISIT, userId), "1", ABSENT_THRESHOLD);
        } catch (Exception e) {
            log.warn("访问时间记录降级（Fail-Open）: userId={}", userId, e);
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

    /** 每日剩余配额探测（只读，不占用配额）：用于在写触发点幂等标记之前提前短路 */
    private boolean hasDailyQuota(Long userId) {
        try {
            String key = String.format(KEY_PROACTIVE_DAILY, userId, LocalDate.now(ZoneId.of("UTC")));
            String used = redisTemplate.opsForValue().get(key);
            if (used == null) {
                return true;
            }
            return Long.parseLong(used) < properties.getProactive().getDailyLimit();
        } catch (Exception e) {
            log.warn("主动消息配额探测 Redis 故障（Fail-Open 跳过本次评估）: userId={}", userId, e);
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
