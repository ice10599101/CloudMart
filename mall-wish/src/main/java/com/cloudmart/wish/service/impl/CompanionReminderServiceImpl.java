package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAiGoal;
import com.cloudmart.wish.enums.GoalStatus;
import com.cloudmart.wish.enums.NotificationChannel;
import com.cloudmart.wish.enums.WishNotificationType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.mq.AiReminderEventProducer;
import com.cloudmart.wish.repository.WishAiGoalMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AiConfigService;
import com.cloudmart.wish.service.CompanionReminderService;
import com.cloudmart.wish.service.NotificationPreferenceService;
import com.cloudmart.wish.service.UserStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 陪伴提醒服务实现（Sprint 2.5，文档 2.5 / 9.2 wish-ai-reminder）。
 *
 * <p>候选集：有 ACTIVE 心愿或 IN_PROGRESS AI 目标的用户（distinct，
 * user_id 游标分批）。文案为模板轮换（陪伴提醒为低频运营触达，
 * 不调用 DashScope——频次/成本可控且必达；个性化文案由预期管理链路承担）。</p>
 *
 * <p>幂等性：Redis 日计数（用户时区当日）保证同一用户当日至多 1 条；
 * 扫描每小时重复执行，已提醒用户自动跳过。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanionReminderServiceImpl implements CompanionReminderService {

    /** 推送时机：用户本地时区 09 点段（文档 9.2） */
    private static final int REMIND_HOUR = 9;

    /** 限频类型标识（Redis key 段） */
    private static final String RATE_TYPE = "companion_reminder";

    /** 候选游标分批大小 */
    private static final int CANDIDATE_BATCH_SIZE = 1000;

    /** 通知标题 */
    private static final String NOTIFY_TITLE = "心愿陪伴提醒";

    /** 模板轮换文案（确定性选择：按当日年内天数取模，同日全站一致） */
    private static final String[] REMIND_TEMPLATES = {
            "你的心愿还在等你呀，今天也向它走近一小步吧——哪怕只是记录一句近况。",
            "陪伴是最长情的告白，你的心愿宇宙今天也想听听你的消息。",
            "别急，慢慢来。每一点微小的坚持，都在让心愿离你更近。",
            "今天风和日丽，适合给心愿浇浇水、打个卡，让它知道你没有忘记它。",
            "心愿的实现藏在每一个平凡的日子里，今天也别忘了给它一点时间。"
    };

    /** 免打扰默认值（wish_ai_config 缺失/解析失败时回退） */
    private static final LocalTime DEFAULT_QUIET_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_QUIET_END = LocalTime.of(8, 0);

    private final WishMapper wishMapper;
    private final WishAiGoalMapper goalMapper;
    private final UserStatService userStatService;
    private final NotificationPreferenceService preferenceService;
    private final AiRateLimiter aiRateLimiter;
    private final AiConfigService aiConfigService;
    private final AiReminderEventProducer reminderProducer;

    @Override
    public RemindResult scanAndRemind() {
        Set<Long> candidates = loadCandidates();
        int reminded = 0;
        int skippedByLocalTime = 0;
        int skippedByQuietHours = 0;
        int skippedByLimit = 0;
        int skippedByPreference = 0;

        int dailyLimit = aiConfigService.getIntConfig(
                AiConfigService.KEY_REMINDER_DAILY_LIMIT, 1);
        LocalTime quietStart = parseQuietTime(AiConfigService.KEY_QUIET_START, DEFAULT_QUIET_START);
        LocalTime quietEnd = parseQuietTime(AiConfigService.KEY_QUIET_END, DEFAULT_QUIET_END);

        for (Long userId : candidates) {
            ZoneId userZone = ZoneId.of(userStatService.getUserTimezone(userId));
            ZonedDateTime localNow = ZonedDateTime.now(userZone);
            if (localNow.getHour() != REMIND_HOUR) {
                skippedByLocalTime++;
                continue;
            }
            if (inQuietHours(localNow.toLocalTime(), quietStart, quietEnd)) {
                skippedByQuietHours++;
                continue;
            }
            if (!aiRateLimiter.checkDailyLimit(userId, RATE_TYPE, dailyLimit, userZone)) {
                skippedByLimit++;
                continue;
            }
            if (!preferenceService.isChannelEnabled(
                    userId, WishNotificationType.AI_REMINDER.name(), NotificationChannel.IN_APP)) {
                skippedByPreference++;
                continue;
            }
            reminderProducer.publishCompanionReminder(userId, NOTIFY_TITLE, pickTemplate());
            reminded++;
        }
        log.info("陪伴提醒扫描完成, candidates={}, reminded={}, byLocalTime={}, byQuietHours={}, "
                        + "byLimit={}, byPreference={}",
                candidates.size(), reminded, skippedByLocalTime, skippedByQuietHours,
                skippedByLimit, skippedByPreference);
        return new RemindResult(candidates.size(), reminded, skippedByLocalTime,
                skippedByQuietHours, skippedByLimit, skippedByPreference);
    }

    /**
     * 加载候选用户：ACTIVE 心愿作者 ∪ IN_PROGRESS AI 目标用户（user_id 游标分批）。
     */
    private Set<Long> loadCandidates() {
        Set<Long> candidates = new LinkedHashSet<>();
        collectWishAuthors(candidates);
        collectGoalUsers(candidates);
        return candidates;
    }

    private void collectWishAuthors(Set<Long> candidates) {
        Long cursor = 0L;
        while (true) {
            List<Wish> batch = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                    .select(Wish::getUserId)
                    .eq(Wish::getStatus, WishStatus.ACTIVE)
                    .isNull(Wish::getDeletedAt)
                    .gt(Wish::getUserId, cursor)
                    .orderByAsc(Wish::getUserId)
                    .last("LIMIT " + CANDIDATE_BATCH_SIZE));
            if (batch.isEmpty()) {
                return;
            }
            batch.forEach(wish -> candidates.add(wish.getUserId()));
            cursor = batch.getLast().getUserId();
            if (batch.size() < CANDIDATE_BATCH_SIZE) {
                return;
            }
        }
    }

    private void collectGoalUsers(Set<Long> candidates) {
        Long cursor = 0L;
        while (true) {
            List<WishAiGoal> batch = goalMapper.selectList(new LambdaQueryWrapper<WishAiGoal>()
                    .select(WishAiGoal::getUserId)
                    .eq(WishAiGoal::getStatus, GoalStatus.IN_PROGRESS)
                    .isNull(WishAiGoal::getDeletedAt)
                    .gt(WishAiGoal::getUserId, cursor)
                    .orderByAsc(WishAiGoal::getUserId)
                    .last("LIMIT " + CANDIDATE_BATCH_SIZE));
            if (batch.isEmpty()) {
                return;
            }
            batch.forEach(goal -> candidates.add(goal.getUserId()));
            cursor = batch.getLast().getUserId();
            if (batch.size() < CANDIDATE_BATCH_SIZE) {
                return;
            }
        }
    }

    /**
     * 免打扰判定：支持跨午夜区间（默认 22:00-08:00）与同日区间两种配置。
     */
    private boolean inQuietHours(LocalTime localTime, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            // 起止相同视为空区间（管理员关闭免打扰）
            return false;
        }
        if (start.isBefore(end)) {
            return !localTime.isBefore(start) && localTime.isBefore(end);
        }
        // 跨午夜：22:00-08:00 → [22:00, 24:00) ∪ [00:00, 08:00)
        return !localTime.isBefore(start) || localTime.isBefore(end);
    }

    private LocalTime parseQuietTime(String configKey, LocalTime defaultValue) {
        String value = aiConfigService.getStringConfig(configKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ex) {
            log.warn("免打扰时段配置非法，使用默认值, key={}, value={}", configKey, value);
            return defaultValue;
        }
    }

    private String pickTemplate() {
        int dayOfYear = LocalDate.now().getDayOfYear();
        return REMIND_TEMPLATES[Math.floorMod(dayOfYear, REMIND_TEMPLATES.length)];
    }
}
