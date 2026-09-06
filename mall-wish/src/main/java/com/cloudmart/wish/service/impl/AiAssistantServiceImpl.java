package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.dto.AiAssistantRequest;
import com.cloudmart.wish.dto.AiGoalCreateRequest;
import com.cloudmart.wish.dto.AiGoalListQuery;
import com.cloudmart.wish.dto.ExpectedActionRecordRequest;
import com.cloudmart.wish.dto.GoalStatusUpdateRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.entity.WishAiGoal;
import com.cloudmart.wish.entity.WishExpectedAtAction;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.enums.GoalStatus;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishAiGoalMapper;
import com.cloudmart.wish.repository.WishExpectedAtActionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AiAssistantService;
import com.cloudmart.wish.service.AiPromptService;
import com.cloudmart.wish.service.AssistantAiClient;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.AiBreakdownGoalVO;
import com.cloudmart.wish.vo.AiBreakdownVO;
import com.cloudmart.wish.vo.AiGoalVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 心愿助手服务实现（Sprint 2.5）。
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>合规前置：未同意 AI 数据处理协议 → 403 WISH_CONSENT_REQUIRED</li>
 *   <li>限频：goalBreakdownDailyLimit 次/日（用户时区），超限 429；Fail-Open</li>
 *   <li>PII 脱敏：外发大模型前移除手机号/邮箱/身份证号（对齐树洞）</li>
 *   <li>Prompt：DB ACTIVE 模板（A/B 分流）优先，空表回退代码默认值</li>
 *   <li>拆解失败（goals 空）→ 503 WISH_AI_UNAVAILABLE，不返回不可执行步骤</li>
 *   <li>对话持久化：USER + ASSISTANT 同事务（scene=GOAL_BREAKDOWN）</li>
 *   <li>目标状态机：CAS 式条件 UPDATE 防并发双写；终态不可逆</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAssistantServiceImpl implements AiAssistantService {

    /** 会话 ID 前缀：goal-{userId}-{随机数}，一次拆解一个会话 */
    private static final String SESSION_PREFIX = "goal-";

    private final WishAiGoalMapper goalMapper;
    private final WishAiConversationMapper conversationMapper;
    private final WishExpectedAtActionMapper expectedAtActionMapper;
    private final WishMapper wishMapper;
    private final ConsentService consentService;
    private final UserStatService userStatService;
    private final AiRateLimiter aiRateLimiter;
    private final AiPrivacySanitizer privacySanitizer;
    private final AssistantAiClient assistantAiClient;
    private final AiPromptService aiPromptService;
    private final WishAiProperties aiProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AiBreakdownVO breakdownGoal(Long userId, AiAssistantRequest request) {
        requireAiConsent(userId);
        ZoneId userZone = ZoneId.of(userStatService.getUserTimezone(userId));
        if (!aiRateLimiter.checkGoalBreakdownDailyLimit(userId, userZone)) {
            throw new BusinessException(WishErrorCodes.WISH_AI_RATE_LIMITED,
                    "今日 AI 助手拆解次数已达上限（" + aiProperties.getGoalBreakdownDailyLimit() + " 次/日）");
        }

        String sanitizedText = privacySanitizer.sanitize(request.text().trim());
        String systemPrompt = aiPromptService.getActivePrompt(
                com.cloudmart.wish.enums.AiPromptScene.GOAL_BREAKDOWN, userId);
        GoalBreakdownParser.ParsedBreakdown breakdown =
                assistantAiClient.generateBreakdown(systemPrompt, sanitizedText);

        if (breakdown.goals().size() < aiProperties.getGoalMinCount()) {
            // 步骤数不足下限视为输出不可用（文档验收：5-10 步骤），不返回给用户
            log.warn("AI拆解步骤数不足下限, userId={}, count={}", userId, breakdown.goals().size());
            throw new BusinessException(WishErrorCodes.WISH_AI_UNAVAILABLE,
                    "AI 拆解结果不可用，请换个描述再试");
        }

        String sessionId = SESSION_PREFIX + userId + "-" + System.currentTimeMillis();
        persistConversation(userId, sessionId, sanitizedText, breakdown);
        return new AiBreakdownVO(breakdown.intent(),
                breakdown.goals(), breakdown.suggestion(), sessionId);
    }

    @Override
    public List<AiGoalVO> createGoals(Long userId, AiGoalCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return transactionTemplate.execute(status -> {
            List<AiGoalVO> created = request.goals().stream().map(item -> {
                WishAiGoal goal = new WishAiGoal();
                goal.setUserId(userId);
                goal.setWishId(request.wishId());
                goal.setTitle(item.title());
                goal.setDescription(item.description());
                goal.setEstimatedDays(item.estimatedDays() != null ? item.estimatedDays() : 7);
                goal.setPriority(item.priority() != null ? item.priority() : 3);
                goal.setStatus(GoalStatus.PENDING);
                goal.setAiSessionId(request.sessionId());
                goalMapper.insert(goal);
                return toGoalVO(goal);
            }).toList();
            log.info("持久化AI拆解目标, userId={}, sessionId={}, count={}",
                    userId, request.sessionId(), created.size());
            return created;
        });
    }

    @Override
    public AiGoalVO updateGoalStatus(Long userId, Long goalId, GoalStatusUpdateRequest request) {
        WishAiGoal goal = goalMapper.selectById(goalId);
        if (goal == null || !goal.getUserId().equals(userId)) {
            // 非本人/不存在统一 404，防存在性探测（对齐胶囊策略）
            throw new BusinessException(WishErrorCodes.WISH_AI_GOAL_NOT_FOUND, "目标不存在");
        }
        GoalStatus current = goal.getStatus();
        GoalStatus target = request.status();
        validateTransition(current, target);

        // CAS 条件更新：status 仍为读取值时才流转（防并发双写）
        LocalDateTime now = LocalDateTime.now();
        WishAiGoal update = new WishAiGoal();
        update.setId(goalId);
        update.setStatus(target);
        if (target == GoalStatus.IN_PROGRESS && current == GoalStatus.PENDING) {
            update.setStartedAt(now);
        }
        if (target == GoalStatus.COMPLETED) {
            update.setCompletedAt(now);
            if (goal.getStartedAt() == null) {
                update.setStartedAt(now);
            }
        }
        int updated = goalMapper.update(update, new LambdaQueryWrapper<WishAiGoal>()
                .eq(WishAiGoal::getId, goalId)
                .eq(WishAiGoal::getStatus, current));
        if (updated == 0) {
            throw new BusinessException(WishErrorCodes.WISH_AI_GOAL_STATUS_INVALID,
                    "目标状态已变更，请刷新后重试");
        }
        return toGoalVO(goalMapper.selectById(goalId));
    }

    @Override
    public GoalPage listMyGoals(Long userId, AiGoalListQuery query) {
        int pageSize = query.safePageSize();
        Long cursor = parseCursor(query.cursor());

        LambdaQueryWrapper<WishAiGoal> wrapper = new LambdaQueryWrapper<WishAiGoal>()
                .eq(WishAiGoal::getUserId, userId)
                .eq(query.status() != null, WishAiGoal::getStatus, query.status())
                .eq(query.wishId() != null, WishAiGoal::getWishId, query.wishId())
                .orderByDesc(WishAiGoal::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursor != null) {
            wrapper.lt(WishAiGoal::getId, cursor);
        }

        List<WishAiGoal> records = goalMapper.selectList(wrapper);
        boolean hasMore = records.size() > pageSize;
        List<WishAiGoal> pageItems = hasMore ? records.subList(0, pageSize) : records;
        List<AiGoalVO> vos = pageItems.stream().map(this::toGoalVO).toList();
        String nextCursor = hasMore ? String.valueOf(pageItems.getLast().getId()) : null;
        return new GoalPage(vos, nextCursor, hasMore);
    }

    @Override
    public void recordExpectedAction(Long userId, ExpectedActionRecordRequest request) {
        Wish wish = wishMapper.selectById(request.wishId());
        if (wish == null || !wish.getUserId().equals(userId)) {
            // 非本人/不存在统一 404，防存在性探测（对齐心愿详情策略）
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        WishExpectedAtAction actionRecord = new WishExpectedAtAction();
        actionRecord.setUserId(userId);
        actionRecord.setWishId(request.wishId());
        actionRecord.setAction(request.action());
        expectedAtActionMapper.insert(actionRecord);
        log.info("预期管理选项埋点, userId={}, wishId={}, action={}",
                userId, request.wishId(), request.action());
    }

    private void requireAiConsent(Long userId) {
        if (!consentService.hasGrantedAiDataProcessing(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_CONSENT_REQUIRED,
                    "使用 AI 功能前需同意 AI 数据处理协议");
        }
    }

    /**
     * 状态机校验：PENDING→IN_PROGRESS/COMPLETED/CANCELLED；
     * IN_PROGRESS→COMPLETED/CANCELLED；终态（COMPLETED/CANCELLED）不可变更。
     */
    private void validateTransition(GoalStatus current, GoalStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == GoalStatus.IN_PROGRESS || target == GoalStatus.COMPLETED
                    || target == GoalStatus.CANCELLED;
            case IN_PROGRESS -> target == GoalStatus.COMPLETED || target == GoalStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!valid) {
            throw new BusinessException(WishErrorCodes.WISH_AI_GOAL_STATUS_INVALID,
                    "目标状态不允许从 " + current + " 变更为 " + target);
        }
    }

    /**
     * 持久化一次拆解对话（USER + ASSISTANT 同事务，scene=GOAL_BREAKDOWN）。
     * ASSISTANT 记录 content 存拆解结果 JSON（goals 数组），供会话回放。
     */
    private void persistConversation(Long userId, String sessionId, String userText,
                                     GoalBreakdownParser.ParsedBreakdown breakdown) {
        transactionTemplate.executeWithoutResult(status -> {
            WishAiConversation userRecord = new WishAiConversation();
            userRecord.setUserId(userId);
            userRecord.setSessionId(sessionId);
            userRecord.setScene(AiScene.GOAL_BREAKDOWN);
            userRecord.setRole(AiConversationRole.USER);
            userRecord.setContent(userText);
            conversationMapper.insert(userRecord);

            String assistantContent = breakdown.goals().stream()
                    .map(goal -> goal.title() + "：" + goal.description())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            WishAiConversation assistantRecord = new WishAiConversation();
            assistantRecord.setUserId(userId);
            assistantRecord.setSessionId(sessionId);
            assistantRecord.setScene(AiScene.GOAL_BREAKDOWN);
            assistantRecord.setRole(AiConversationRole.ASSISTANT);
            assistantRecord.setContent("【意图】" + breakdown.intent() + "\n" + assistantContent
                    + "\n【建议】" + breakdown.suggestion());
            conversationMapper.insert(assistantRecord);
        });
    }

    private AiGoalVO toGoalVO(WishAiGoal goal) {
        return new AiGoalVO(goal.getId(), goal.getWishId(), goal.getTitle(),
                goal.getDescription(), goal.getEstimatedDays(), goal.getPriority(),
                goal.getStatus(), goal.getAiSessionId(), goal.getStartedAt(),
                goal.getCompletedAt(), goal.getCreatedAt());
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "分页游标格式不正确");
        }
    }
}
