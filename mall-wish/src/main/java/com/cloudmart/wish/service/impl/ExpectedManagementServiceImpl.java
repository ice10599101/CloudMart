package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishExpectedAtAction;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.enums.ExpectedActionType;
import com.cloudmart.wish.enums.NotificationChannel;
import com.cloudmart.wish.enums.WishNotificationType;
import com.cloudmart.wish.mq.AiReminderEventProducer;
import com.cloudmart.wish.repository.WishExpectedAtActionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AiConfigService;
import com.cloudmart.wish.service.AiPromptService;
import com.cloudmart.wish.service.AssistantAiClient;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.ExpectedManagementService;
import com.cloudmart.wish.service.NotificationPreferenceService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.WishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 预期管理服务实现（Sprint 2.5，文档 2.5 / 第三章 3.1）。
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>通知必达：限频/偏好过滤后逐心愿下发；AI 文案失败降级模板</li>
 *   <li>合规：未同意 AI 数据处理的用户走模板文案，心愿内容不外发大模型服务</li>
 *   <li>限频：{@code expected.daily_limit}（默认 3）条/日/用户（用户时区），
 *       Redis Fail-Open（超限仅产生额外推送，不破坏一致性）</li>
 *   <li>MQ 发送 Fail-Open：失败不阻断后续心愿处理（文档允许推送降级）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpectedManagementServiceImpl implements ExpectedManagementService {

    /** 限频类型标识（Redis key 段） */
    private static final String RATE_TYPE = "expected_mgmt";

    /** 通知标题 */
    private static final String NOTIFY_TITLE = "心愿到期提醒";

    private final WishMapper wishMapper;
    private final WishExpectedAtActionMapper actionMapper;
    private final UserStatService userStatService;
    private final ConsentService consentService;
    private final NotificationPreferenceService preferenceService;
    private final AiRateLimiter aiRateLimiter;
    private final AiPrivacySanitizer privacySanitizer;
    private final AiPromptService aiPromptService;
    private final AssistantAiClient assistantAiClient;
    private final AiConfigService aiConfigService;
    private final AiReminderEventProducer reminderProducer;

    @Override
    public NotifyResult notifyExpiredWishes(List<WishService.OverdueWishInfo> wishes) {
        int notified = 0;
        int skippedByLimit = 0;
        int skippedByPreference = 0;
        for (WishService.OverdueWishInfo wish : wishes) {
            ZoneId userZone = ZoneId.of(userStatService.getUserTimezone(wish.userId()));
            int dailyLimit = aiConfigService.getIntConfig(
                    AiConfigService.KEY_EXPECTED_DAILY_LIMIT, 3);
            if (!aiRateLimiter.checkDailyLimit(wish.userId(), RATE_TYPE, dailyLimit, userZone)) {
                skippedByLimit++;
                continue;
            }
            if (!preferenceService.isChannelEnabled(
                    wish.userId(), WishNotificationType.CHECKIN_REMINDER.name(), NotificationChannel.IN_APP)) {
                skippedByPreference++;
                continue;
            }
            String guideText = buildGuideText(wish);
            reminderProducer.publishExpectedGuide(wish.userId(), wish.wishId(),
                    NOTIFY_TITLE, guideText);
            notified++;
        }
        log.info("预期管理通知下发完成, total={}, notified={}, skippedByLimit={}, skippedByPreference={}",
                wishes.size(), notified, skippedByLimit, skippedByPreference);
        return new NotifyResult(notified, skippedByLimit, skippedByPreference);
    }

    @Override
    public void recordAction(Long userId, Long wishId, ExpectedActionType action) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !wish.getUserId().equals(userId)) {
            // 非本人/不存在统一 404，防存在性探测（对齐胶囊策略）
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        WishExpectedAtAction record = new WishExpectedAtAction();
        record.setUserId(userId);
        record.setWishId(wishId);
        record.setAction(action);
        actionMapper.insert(record);
        log.info("预期管理选项埋点, userId={}, wishId={}, action={}", userId, wishId, action);
    }

    /**
     * 生成引导文案：AI 同意 + 大模型可用 → 个性化文案；
     * 否则模板降级（通知必达，文案是增强）。
     */
    private String buildGuideText(WishService.OverdueWishInfo wish) {
        long overdueDays = Math.max(0, Duration.between(
                wish.expectedAt(), LocalDateTime.now()).toDays());
        if (!consentService.hasGrantedAiDataProcessing(wish.userId())) {
            return templateGuide(wish.title(), overdueDays);
        }
        try {
            String sanitizedTitle = privacySanitizer.sanitize(wish.title());
            String context = "心愿标题：" + sanitizedTitle + "；已过期天数：" + overdueDays;
            String systemPrompt = aiPromptService.getActivePrompt(
                    AiPromptScene.EXPECTED_GUIDE, wish.userId());
            String content = assistantAiClient.generateText(systemPrompt, context);
            if (content != null && !content.isBlank()) {
                return content.trim();
            }
            return templateGuide(wish.title(), overdueDays);
        } catch (Exception ex) {
            log.warn("预期管理AI文案生成失败，模板降级, wishId={}, error={}",
                    wish.wishId(), ex.getMessage());
            return templateGuide(wish.title(), overdueDays);
        }
    }

    /**
     * 模板降级文案（不依赖大模型；与 Prompt 契约同构：提及心愿 + 鼓励 + 引导问句）。
     */
    private String templateGuide(String title, long overdueDays) {
        return "你的心愿《" + title + "》已到期 " + overdueDays
                + " 天，心愿的旅程有时需要重新规划。要不要延长预期、让 AI 调整目标，"
                + "或者把它封存进时间胶囊？";
    }
}
