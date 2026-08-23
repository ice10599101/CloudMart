package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.ExpectedActionType;
import jakarta.validation.constraints.NotNull;

/**
 * 预期管理选项埋点请求（POST /ai/expected-actions，文档 2.5 数据回收）。
 *
 * <p>记录用户对"心愿到期"通知 3 选项按钮的选择，
 * 用于"延长/调整/转入胶囊"转化率分析。</p>
 *
 * @param wishId 到期心愿 ID
 * @param action 用户选择：EXTEND（延长预期）/ ADJUST（调整目标）/ TO_CAPSULE（转入胶囊）
 */
public record ExpectedActionRecordRequest(
        @NotNull(message = "心愿 ID 不能为空") Long wishId,
        @NotNull(message = "选项类型不能为空") ExpectedActionType action) {
}
