package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AiAssistantRequest;
import com.cloudmart.wish.dto.AiConfigUpdateRequest;
import com.cloudmart.wish.dto.AiGoalCreateRequest;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.ExpectedActionRecordRequest;
import com.cloudmart.wish.dto.GoalStatusUpdateRequest;
import com.cloudmart.wish.dto.GrantConsentRequest;
import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import com.cloudmart.wish.enums.ExpectedActionType;
import com.cloudmart.wish.enums.GoalStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.AiAssistantService;
import com.cloudmart.wish.service.AiConfigService;
import com.cloudmart.wish.service.AnnualReportService;
import com.cloudmart.wish.service.CompanionReminderService;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.service.impl.GoalBreakdownParser.ParsedBreakdown;
import com.cloudmart.wish.vo.AiBreakdownGoalVO;
import com.cloudmart.wish.vo.AiBreakdownVO;
import com.cloudmart.wish.vo.AiGoalVO;
import com.cloudmart.wish.vo.AnnualReportVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 心愿助手链路集成测试（真实 MySQL 持久化 + 真实 Redis 限频，
 * Sprint 2.5）。
 *
 * <p>覆盖拆解全链路：consent 前置（403）→ 日限频（真实 Redis 计数 429）→
 * PII 脱敏外发 → 拆解结果不可用降级（503）→ USER+ASSISTANT 对话双记录
 * 落库（scene=GOAL_BREAKDOWN）；目标状态机 CAS 流转与终态 409；
 * 预期管理埋点归属校验；年度报告真实 DB 聚合（打卡去重/分类计数）；
 * 陪伴提醒四道闸门（本地 09 点段/免打扰/日限频/偏好）。</p>
 *
 * <p>AssistantAiClient 以 MockitoBean 替换（外部付费 API；降级链路需断言
 * "未外发"），其余组件（ConsentService/AiRateLimiter/AiPrivacySanitizer/
 * AiPromptService/持久化）走真实链路。陪伴提醒时区用 UTC 偏移构造目标
 * 本地小时（依赖运行时钟的整点小时值，跨整点毫秒级窗口理论上存在抖动，
 * 与生产"每小时扫描命中 09 点段"语义一致）。</p>
 */
@DisplayName("AI 心愿助手链路集成测试")
class AiAssistantIntegrationTest extends WishIntegrationTestBase {

    private static final long USER_ID = 3101L;
    private static final long OTHER_ID = 3102L;
    private static final long REPORT_USER_ID = 3103L;
    private static final String RATE_LIMIT_KEY =
            "wish:rate:user:" + USER_ID + ":ai_goal_breakdown";
    private static final String COMPANION_DESTINATION = "wish-events:companion-reminder";

    @Autowired
    private AiAssistantService aiAssistantService;

    @Autowired
    private AnnualReportService annualReportService;

    @Autowired
    private CompanionReminderService companionReminderService;

    @Autowired
    private WishService wishService;

    @Autowired
    private ConsentService consentService;

    @Autowired
    private AiConfigService aiConfigService;

    @BeforeEach
    void setUp() {
        stubUserFeign();
        grantAiConsent(USER_ID, ConsentAction.GRANT);
    }

    private void grantAiConsent(long userId, ConsentAction action) {
        consentService.recordConsent(userId,
                new GrantConsentRequest(ConsentType.AI_DATA_PROCESSING, "v1.0", action, null),
                "127.0.0.1", "it-test-agent");
    }

    private AiBreakdownVO breakdown(String text) {
        return aiAssistantService.breakdownGoal(USER_ID, new AiAssistantRequest(text, null));
    }

    private ParsedBreakdown mockBreakdown(int goalCount) {
        List<AiBreakdownGoalVO> goals = new ArrayList<>();
        for (int i = 1; i <= goalCount; i++) {
            goals.add(new AiBreakdownGoalVO("步骤" + i, "循序渐进完成第 " + i + " 步", 7, 3));
        }
        return new ParsedBreakdown("减肥10斤", goals, "慢慢来比较快");
    }

    private Integer conversationCount(String sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wish_ai_conversation WHERE session_id = ?",
                Integer.class, sessionId);
    }

    private Long createActiveWish(long userId, String title) {
        Long categoryId = seedCategory("IT_AI_" + title);
        WishCreateResultVO created = wishService.createWish(userId, new CreateWishRequest(
                title, "AI 助手集成测试心愿", null, categoryId,
                List.of("测试"), WishVisibility.PUBLIC, null, null, null));
        return created.id();
    }

    @Nested
    @DisplayName("合规前置与限频")
    class ConsentAndRateLimit {

        @Test
        @DisplayName("未同意 AI 数据协议：403 WISH_CONSENT_REQUIRED，不调 AI 不落对话")
        void rejectWhenConsentMissing() {
            jdbcTemplate.update("DELETE FROM wish_consent WHERE user_id = ?", USER_ID);

            assertThatThrownBy(() -> breakdown("我想减肥10斤"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_CONSENT_REQUIRED);

            verify(assistantAiClient, never()).generateBreakdown(anyString(), anyString());
        }

        @Test
        @DisplayName("GRANT 后再 WITHDRAW：最新记录为准，重新返回 403")
        void rejectAfterWithdraw() {
            grantAiConsent(USER_ID, ConsentAction.WITHDRAW);

            assertThatThrownBy(() -> breakdown("我想坚持早起"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_CONSENT_REQUIRED);
        }

        @Test
        @DisplayName("超过每日 10 次限频：429 WISH_AI_RATE_LIMITED，不调 AI 不落对话")
        void rateLimitExceededRejected() {
            // 预置 Redis 计数至当日上限（真实 AiRateLimiter INCR 后 = 11 > 10）
            redisTemplate.opsForValue().set(RATE_LIMIT_KEY, "10");

            assertThatThrownBy(() -> breakdown("第 11 次拆解"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_AI_RATE_LIMITED);

            verify(assistantAiClient, never()).generateBreakdown(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("拆解链路与持久化")
    class BreakdownChain {

        @Test
        @DisplayName("拆解成功：USER+ASSISTANT 双记录同事务落库，VO 字段与 sessionId 正确")
        void persistsConversationAndReturnsBreakdown() {
            when(assistantAiClient.generateBreakdown(anyString(), anyString()))
                    .thenReturn(mockBreakdown(5));

            AiBreakdownVO vo = breakdown("我想减肥10斤，从今天开始");

            assertThat(vo.intent()).isEqualTo("减肥10斤");
            assertThat(vo.goals()).hasSize(5);
            assertThat(vo.suggestion()).isEqualTo("慢慢来比较快");
            assertThat(vo.sessionId()).startsWith("goal-" + USER_ID + "-");

            assertThat(conversationCount(vo.sessionId())).isEqualTo(2);
            String userContent = jdbcTemplate.queryForObject(
                    "SELECT content FROM wish_ai_conversation WHERE session_id = ? AND role = 'USER'",
                    String.class, vo.sessionId());
            String assistantContent = jdbcTemplate.queryForObject(
                    "SELECT content FROM wish_ai_conversation WHERE session_id = ? AND role = 'ASSISTANT'",
                    String.class, vo.sessionId());
            assertThat(userContent).isEqualTo("我想减肥10斤，从今天开始");
            assertThat(assistantContent).contains("【意图】减肥10斤").contains("【建议】慢慢来比较快");
        }

        @Test
        @DisplayName("PII 脱敏：手机号不外发 DashScope，落库内容同为脱敏文本")
        void sanitizesPiiBeforeSendingToAi() {
            when(assistantAiClient.generateBreakdown(anyString(), anyString()))
                    .thenReturn(mockBreakdown(5));

            AiBreakdownVO vo = breakdown("我想减肥10斤，联系我 13800138000");

            ArgumentCaptor<String> outboundCaptor = ArgumentCaptor.forClass(String.class);
            verify(assistantAiClient).generateBreakdown(anyString(), outboundCaptor.capture());
            assertThat(outboundCaptor.getValue()).doesNotContain("13800138000");

            String storedContent = jdbcTemplate.queryForObject(
                    "SELECT content FROM wish_ai_conversation WHERE session_id = ? AND role = 'USER'",
                    String.class, vo.sessionId());
            assertThat(storedContent).doesNotContain("13800138000");
        }

        @Test
        @DisplayName("拆解步骤数不足下限：503 WISH_AI_UNAVAILABLE，不落对话")
        void unusableBreakdownRejected() {
            when(assistantAiClient.generateBreakdown(anyString(), anyString()))
                    .thenReturn(mockBreakdown(3));

            assertThatThrownBy(() -> breakdown("模糊描述"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_AI_UNAVAILABLE);

            Integer aiGoalSessions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_ai_conversation WHERE scene = 'GOAL_BREAKDOWN'",
                    Integer.class);
            assertThat(aiGoalSessions).isZero();
        }
    }

    @Nested
    @DisplayName("目标勾选持久化与状态机")
    class GoalLifecycle {

        private Long goalId;

        @BeforeEach
        void seedGoal() {
            List<AiGoalVO> created = aiAssistantService.createGoals(USER_ID, new AiGoalCreateRequest(
                    "goal-3101-it", null,
                    List.of(new AiGoalCreateRequest.GoalItem("第一步", "迈出第一步", 7, 2))));
            goalId = created.getFirst().id();
        }

        @Test
        @DisplayName("勾选持久化：wish_ai_goal 落库 status=PENDING 且关联会话")
        void createsPendingGoal() {
            assertThat(goalId).isNotNull();
            var row = jdbcTemplate.queryForMap(
                    "SELECT user_id, status, ai_session_id, deleted_at FROM wish_ai_goal WHERE id = ?",
                    goalId);
            assertThat(row.get("user_id")).isEqualTo(USER_ID);
            assertThat(row.get("status")).isEqualTo("PENDING");
            assertThat(row.get("ai_session_id")).isEqualTo("goal-3101-it");
            assertThat(row.get("deleted_at")).isNull();
        }

        @Test
        @DisplayName("状态流转：PENDING→IN_PROGRESS→COMPLETED，时间戳与状态落库正确")
        void transitionsToCompleted() {
            AiGoalVO started = aiAssistantService.updateGoalStatus(
                    USER_ID, goalId, new GoalStatusUpdateRequest(GoalStatus.IN_PROGRESS));
            assertThat(started.status()).isEqualTo(GoalStatus.IN_PROGRESS);
            assertThat(started.startedAt()).isNotNull();

            AiGoalVO completed = aiAssistantService.updateGoalStatus(
                    USER_ID, goalId, new GoalStatusUpdateRequest(GoalStatus.COMPLETED));
            assertThat(completed.status()).isEqualTo(GoalStatus.COMPLETED);
            assertThat(completed.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("终态不可逆：COMPLETED 再流转 409 WISH_AI_GOAL_STATUS_INVALID")
        void terminalGoalRejected() {
            aiAssistantService.updateGoalStatus(USER_ID, goalId,
                    new GoalStatusUpdateRequest(GoalStatus.COMPLETED));

            assertThatThrownBy(() -> aiAssistantService.updateGoalStatus(
                    USER_ID, goalId, new GoalStatusUpdateRequest(GoalStatus.IN_PROGRESS)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_AI_GOAL_STATUS_INVALID);
        }

        @Test
        @DisplayName("非法迁移：PENDING→PENDING 409")
        void invalidTransitionRejected() {
            assertThatThrownBy(() -> aiAssistantService.updateGoalStatus(
                    USER_ID, goalId, new GoalStatusUpdateRequest(GoalStatus.PENDING)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_AI_GOAL_STATUS_INVALID);
        }

        @Test
        @DisplayName("非本人目标：404 WISH_AI_GOAL_NOT_FOUND（防存在性探测）")
        void nonOwnerGoalRejected() {
            assertThatThrownBy(() -> aiAssistantService.updateGoalStatus(
                    OTHER_ID, goalId, new GoalStatusUpdateRequest(GoalStatus.IN_PROGRESS)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_AI_GOAL_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("预期管理选项埋点")
    class ExpectedActionTracking {

        @Test
        @DisplayName("本人上报：wish_expected_at_action 落库")
        void recordsOwnerAction() {
            Long wishId = createActiveWish(USER_ID, "埋点本人");

            aiAssistantService.recordExpectedAction(USER_ID,
                    new ExpectedActionRecordRequest(wishId, ExpectedActionType.EXTEND));

            var row = jdbcTemplate.queryForMap(
                    "SELECT user_id, wish_id, action FROM wish_expected_at_action "
                            + "WHERE wish_id = ?", wishId);
            assertThat(row.get("user_id")).isEqualTo(USER_ID);
            assertThat(row.get("action")).isEqualTo("EXTEND");
        }

        @Test
        @DisplayName("非本人心愿：404 WISH_NOT_FOUND（防存在性探测）")
        void nonOwnerWishRejected() {
            Long wishId = createActiveWish(USER_ID, "埋点非本人");

            assertThatThrownBy(() -> aiAssistantService.recordExpectedAction(
                    OTHER_ID, new ExpectedActionRecordRequest(wishId, ExpectedActionType.TO_CAPSULE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_expected_at_action WHERE wish_id = ?",
                    Integer.class, wishId);
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("年度报告聚合")
    class AnnualReport {

        @Test
        @DisplayName("真实 DB 聚合：实现数/打卡去重天数/热门分类/里程碑统计正确")
        void aggregatesStatisticsFromRealData() {
            seedUserStat(REPORT_USER_ID, 0);
            Long categoryId = seedCategory("IT_ANNUAL_REPORT");
            WishCreateResultVO wish = wishService.createWish(REPORT_USER_ID, new CreateWishRequest(
                    "年度报告测试心愿", "聚合统计", null, categoryId,
                    List.of("测试"), WishVisibility.PUBLIC, null, null, null));
            jdbcTemplate.update(
                    "UPDATE wish SET status = 'FULFILLED', fulfilled_at = NOW() WHERE id = ?", wish.id());
            LocalDate today = LocalDate.now();
            // 同日两条打卡 → 去重后 1 天
            for (int i = 0; i < 2; i++) {
                jdbcTemplate.update("""
                        INSERT INTO wish_checkin (id, wish_id, user_id, checkin_date, content,
                                is_makeup, starlight_granted, created_at, updated_at)
                        VALUES (?, ?, ?, ?, '打卡', 0, 1, NOW(), NOW())
                        """, System.nanoTime(), wish.id(), REPORT_USER_ID, today);
            }
            jdbcTemplate.update("""
                    INSERT INTO wish_growth_record (id, wish_id, user_id, type, content, media_urls,
                            progress_delta, audit_status, is_visible, created_at, updated_at)
                    VALUES (?, ?, ?, 'TEXT', '坚持打卡的第一周', NULL, 10, 'APPROVED', 1, NOW(), NOW())
                    """, System.nanoTime(), wish.id(), REPORT_USER_ID);

            AnnualReportVO report = annualReportService.getOrGenerateReport(
                    REPORT_USER_ID, today.getYear());

            assertThat(report.year()).isEqualTo(today.getYear());
            assertThat(report.fulfilledCount()).isEqualTo(1);
            assertThat(report.totalCheckinDays()).isEqualTo(1);
            assertThat(report.topCategories()).hasSize(1);
            assertThat(report.topCategories().getFirst().name()).isEqualTo("测试分类-IT_ANNUAL_REPORT");
            assertThat(report.topCategories().getFirst().count()).isEqualTo(1);
            assertThat(report.milestones()).hasSize(1);
            assertThat(report.milestones().getFirst().title()).isEqualTo("记下了一段成长");
            assertThat(report.growthSummary()).contains(String.valueOf(today.getYear())).isNotBlank();
        }

        @Test
        @DisplayName("全年无数据：统计全 0，模板文案启程版，不提交 AI 任务")
        void emptyYearReturnsTemplateWithoutAiTask() {
            seedUserStat(REPORT_USER_ID + 1, 0);

            AnnualReportVO report = annualReportService.getOrGenerateReport(
                    REPORT_USER_ID + 1, LocalDate.now().getYear());

            assertThat(report.fulfilledCount()).isZero();
            assertThat(report.totalCheckinDays()).isZero();
            assertThat(report.milestones()).isEmpty();
            assertThat(report.topCategories()).isEmpty();
            assertThat(report.growthSummary()).contains("刚刚启程");
            // 无数据不值得 AI 总结：不触发生成（AI 客户端零调用）
            verify(assistantAiClient, never()).generateText(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("陪伴提醒扫描")
    class CompanionReminder {

        /** 构造本地小时为 targetHour 的 UTC 偏移时区（依赖运行时钟小时值）。 */
        private String zoneWithLocalHour(int targetHour) {
            int utcHour = LocalDateTime.now(ZoneOffset.UTC).getHour();
            int offset = Math.floorMod(targetHour - utcHour, 24);
            if (offset > 18) {
                offset -= 24;
            }
            return offset >= 0
                    ? String.format("UTC+%02d", offset)
                    : String.format("UTC-%02d", -offset);
        }

        private void seedReminderCandidate(long userId, String timezone) {
            jdbcTemplate.update("""
                    INSERT INTO wish_user_stat (user_id, starlight_balance, timezone, created_at, updated_at)
                    VALUES (?, 0, ?, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE timezone = VALUES(timezone)
                    """, userId, timezone);
            Long categoryId = seedCategory("IT_COMPANION_" + userId);
            wishService.createWish(userId, new CreateWishRequest(
                    "陪伴提醒测试心愿", "扫描候选", null, categoryId,
                    List.of("测试"), WishVisibility.PUBLIC, null, null, null));
        }

        @Test
        @DisplayName("本地 09 点段：发送 1 条 companion-reminder，文案为轮换模板")
        void sendsReminderAtLocalNine() {
            seedReminderCandidate(USER_ID, zoneWithLocalHour(9));

            CompanionReminderService.RemindResult result = companionReminderService.scanAndRemind();

            assertThat(result.candidates()).isEqualTo(1);
            assertThat(result.reminded()).isEqualTo(1);

            ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
            verify(rocketMQTemplate).syncSend(eq(COMPANION_DESTINATION), messageCaptor.capture());
            Object payload = messageCaptor.getValue();
            // 五套轮换模板均含"心愿"；record toString 校验消息信封字段
            assertThat(payload.toString()).contains("心愿").contains("userId=" + USER_ID);
        }

        @Test
        @DisplayName("免打扰时段覆盖 09 点段（临时改 08:00-10:00）：跳过不推送")
        void quietHoursSkipped() {
            seedReminderCandidate(USER_ID, zoneWithLocalHour(9));
            // 默认 22:00-08:00 不含 09 点段，经服务方法临时改配置（更新即失效缓存，
            // 避免 SQL 直改引发跨用例缓存污染），finally 恢复默认值
            aiConfigService.updateConfig(AiConfigService.KEY_QUIET_START,
                    new AiConfigUpdateRequest("08:00"), 1L);
            aiConfigService.updateConfig(AiConfigService.KEY_QUIET_END,
                    new AiConfigUpdateRequest("10:00"), 1L);
            try {
                CompanionReminderService.RemindResult result = companionReminderService.scanAndRemind();

                assertThat(result.skippedByQuietHours()).isEqualTo(1);
                assertThat(result.reminded()).isZero();
                verify(rocketMQTemplate, never()).syncSend(
                        contains(COMPANION_DESTINATION), any(Object.class));
            } finally {
                aiConfigService.updateConfig(AiConfigService.KEY_QUIET_START,
                        new AiConfigUpdateRequest("22:00"), 1L);
                aiConfigService.updateConfig(AiConfigService.KEY_QUIET_END,
                        new AiConfigUpdateRequest("08:00"), 1L);
            }
        }

        @Test
        @DisplayName("非 09 点段（本地 15 点）：跳过不推送")
        void nonNineHourSkipped() {
            seedReminderCandidate(USER_ID, zoneWithLocalHour(15));

            CompanionReminderService.RemindResult result = companionReminderService.scanAndRemind();

            assertThat(result.skippedByLocalTime()).isEqualTo(1);
            assertThat(result.reminded()).isZero();
        }

        @Test
        @DisplayName("日限频（当日已提醒 1 条）：跳过不重复推送")
        void dailyLimitSkipped() {
            seedReminderCandidate(USER_ID, zoneWithLocalHour(9));
            // 预置当日已用 1 次（真实 AiRateLimiter INCR 后 = 2 > 1）
            redisTemplate.opsForValue().set(
                    "wish:rate:user:" + USER_ID + ":companion_reminder", "1");

            CompanionReminderService.RemindResult result = companionReminderService.scanAndRemind();

            assertThat(result.skippedByLimit()).isEqualTo(1);
            assertThat(result.reminded()).isZero();
            verify(rocketMQTemplate, never()).syncSend(
                    contains(COMPANION_DESTINATION), any(Object.class));
        }

        @Test
        @DisplayName("通知偏好关闭（AI_REMINDER×IN_APP=0）：跳过不推送")
        void preferenceOffSkipped() {
            seedReminderCandidate(USER_ID, zoneWithLocalHour(9));
            jdbcTemplate.update("""
                    INSERT INTO wish_notification_preference (id, user_id, notification_type, channel, enabled)
                    VALUES (?, ?, 'AI_REMINDER', 'IN_APP', 0)
                    """, System.nanoTime(), USER_ID);

            CompanionReminderService.RemindResult result = companionReminderService.scanAndRemind();

            assertThat(result.skippedByPreference()).isEqualTo(1);
            assertThat(result.reminded()).isZero();
            verify(rocketMQTemplate, never()).syncSend(
                    contains(COMPANION_DESTINATION), any(Object.class));
        }
    }
}
