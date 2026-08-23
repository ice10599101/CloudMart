package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.annotation.Idempotent;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.AiAssistantRequest;
import com.cloudmart.wish.dto.AiGoalCreateRequest;
import com.cloudmart.wish.dto.AiGoalListQuery;
import com.cloudmart.wish.dto.ExpectedActionRecordRequest;
import com.cloudmart.wish.dto.GoalStatusUpdateRequest;
import com.cloudmart.wish.service.AiAssistantService;
import com.cloudmart.wish.service.AnnualReportService;
import com.cloudmart.wish.vo.AiBreakdownVO;
import com.cloudmart.wish.vo.AiGoalVO;
import com.cloudmart.wish.vo.AnnualReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 心愿助手 Controller（文档 2.5/2.11，Sprint 2.5）。
 *
 * <p>链路：AI 数据同意校验 → Redis 日限频（用户时区）→ PII 脱敏 →
 * Prompt 模板（DB A/B 优先）→ DashScope 5-10 步骤拆解 → 对话持久化。</p>
 *
 * <p>错误码：403 WISH_CONSENT_REQUIRED（未同意 AI 数据处理协议）/
 * 429 WISH_AI_RATE_LIMITED（拆解日限频）/ 503 WISH_AI_UNAVAILABLE
 * （AI 输出不可用）/ 404 WISH_AI_GOAL_NOT_FOUND（目标不存在或非本人）/
 * 409 WISH_AI_GOAL_STATUS_INVALID（终态再变更/并发冲突）。</p>
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 心愿助手", description = "意图分析 + 目标拆解 + 步骤勾选持久化与状态流转（Sprint 2.5）")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final AnnualReportService annualReportService;

    @PostMapping("/assistant")
    @Operation(summary = "意图分析+目标拆解", description = "输入心愿/目标描述，AI 返回意图概括"
            + "+5-10 个拆解步骤+鼓励建议；使用前须同意 AI 数据处理协议（POST /my/consents）；"
            + "单用户 10 次/日（按用户时区）。重复提交请携带 X-Idempotency-Key 请求头；"
            + "goals 为空不返回（503），避免不可执行步骤")
    @SentinelResource("WISH_AI_ASSISTANT_BREAKDOWN")
    @Idempotent(prefix = "wish-ai-assistant", ttl = 10)
    public ApiResponse<AiBreakdownVO> breakdownGoal(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody AiAssistantRequest request) {
        return ApiResponse.ok(aiAssistantService.breakdownGoal(userId, request));
    }

    @PostMapping("/goals")
    @Operation(summary = "勾选步骤持久化", description = "用户在拆解结果中勾选步骤后提交，"
            + "批量插入 wish_ai_goal（status=PENDING）；sessionId 关联拆解对话记录")
    @SentinelResource("WISH_AI_GOAL_CREATE")
    public ApiResponse<List<AiGoalVO>> createGoals(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody AiGoalCreateRequest request) {
        return ApiResponse.ok(aiAssistantService.createGoals(userId, request));
    }

    @PutMapping("/goals/{goalId}")
    @Operation(summary = "目标状态流转", description = "PENDING→IN_PROGRESS→COMPLETED；"
            + "非终态可 CANCELLED；终态再变更 409；并发冲突 409（刷新重试）；仅本人可操作")
    @SentinelResource("WISH_AI_GOAL_STATUS_UPDATE")
    public ApiResponse<AiGoalVO> updateGoalStatus(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "目标 ID", required = true) @PathVariable Long goalId,
            @Valid @RequestBody GoalStatusUpdateRequest request) {
        return ApiResponse.ok(aiAssistantService.updateGoalStatus(userId, goalId, request));
    }

    @GetMapping("/goals")
    @Operation(summary = "我的 AI 目标列表", description = "id 倒序游标分页；"
            + "可按状态/关联心愿筛选")
    @SentinelResource("WISH_AI_GOAL_LIST")
    public ApiResponse<List<AiGoalVO>> listMyGoals(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            AiGoalListQuery query) {
        AiAssistantService.GoalPage page = aiAssistantService.listMyGoals(userId, query);
        return ApiResponse.okWithCursor(page.records(), query.safePageSize(),
                page.nextCursor(), page.hasMore());
    }

    @PostMapping("/expected-actions")
    @Operation(summary = "预期管理选项埋点", description = "用户点击\"心愿到期\"通知的 3 选项按钮"
            + "（EXTEND 延长预期 / ADJUST 调整目标 / TO_CAPSULE 转入胶囊）时上报，"
            + "用于转化率分析（文档 2.5 数据回收 wish_expected_at_action）；"
            + "非本人/不存在心愿 404")
    @SentinelResource("WISH_AI_EXPECTED_ACTION")
    public ApiResponse<Void> recordExpectedAction(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ExpectedActionRecordRequest request) {
        aiAssistantService.recordExpectedAction(userId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/annual-report")
    @Operation(summary = "年度报告", description = "聚合该年实现心愿数/打卡天数/成长里程碑/热门分类；"
            + "growthSummary 异步 AI 生成（首次请求返回模板文案并触发后台任务，"
            + "稍后重查返回 AI 版）；报告不持久化，结果缓存 168h（可配置）；"
            + "仅含本人数据，不含其他用户隐私")
    @SentinelResource("WISH_AI_ANNUAL_REPORT")
    public ApiResponse<AnnualReportVO> getAnnualReport(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "报告年度", required = true)
            @RequestParam int year) {
        return ApiResponse.ok(annualReportService.getOrGenerateReport(userId, year));
    }
}
