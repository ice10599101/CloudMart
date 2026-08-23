package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AiAssistantRequest;
import com.cloudmart.wish.dto.AiGoalCreateRequest;
import com.cloudmart.wish.dto.AiGoalListQuery;
import com.cloudmart.wish.dto.ExpectedActionRecordRequest;
import com.cloudmart.wish.dto.GoalStatusUpdateRequest;
import com.cloudmart.wish.vo.AiBreakdownVO;
import com.cloudmart.wish.vo.AiGoalVO;

import java.util.List;

/**
 * AI 心愿助手服务（Sprint 2.5，文档 2.11 / 2.5）。
 *
 * <p>链路对齐树洞：AI 数据同意校验 → Redis 限频 → PII 脱敏 →
 * Prompt 模板（DB 优先/代码回退）→ DashScope → 对话持久化。</p>
 */
public interface AiAssistantService {

    /**
     * 意图分析 + 目标拆解（POST /wish/ai/assistant）。
     *
     * <p>输出不可执行（goals 为空）时抛 {@code WISH_AI_UNAVAILABLE}；
     * 对话（USER+ASSISTANT）写入 wish_ai_conversation（scene=GOAL_BREAKDOWN）。</p>
     */
    AiBreakdownVO breakdownGoal(Long userId, AiAssistantRequest request);

    /**
     * 勾选步骤持久化（POST /wish/ai/goals）：status=PENDING 批量插入 ㊱b。
     */
    List<AiGoalVO> createGoals(Long userId, AiGoalCreateRequest request);

    /**
     * 目标状态流转（PUT /wish/ai/goals/{goalId}）：
     * PENDING→IN_PROGRESS→COMPLETED；非终态→CANCELLED；
     * 终态再变更抛 409 WISH_AI_GOAL_STATUS_INVALID；仅本人可操作。
     */
    AiGoalVO updateGoalStatus(Long userId, Long goalId, GoalStatusUpdateRequest request);

    /**
     * 我的 AI 目标列表（cursor 分页）。
     */
    GoalPage listMyGoals(Long userId, AiGoalListQuery query);

    /**
     * 预期管理选项埋点（POST /ai/expected-actions，文档 2.5 数据回收）。
     *
     * <p>记录用户对"心愿到期"通知 3 选项（延长预期/调整目标/转入胶囊）
     * 的选择行为；非本人/不存在心愿返回 404；埋点只追加不更新。</p>
     */
    void recordExpectedAction(Long userId, ExpectedActionRecordRequest request);

    /**
     * 目标分页结果。
     */
    record GoalPage(List<AiGoalVO> records, String nextCursor, boolean hasMore) {
    }
}
