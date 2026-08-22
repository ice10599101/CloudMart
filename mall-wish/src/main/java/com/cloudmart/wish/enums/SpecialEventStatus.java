package com.cloudmart.wish.enums;

/**
 * 全站特殊事件状态（Sprint 2.2，文档第二章 2. 特殊事件）。
 *
 * <p>读取方惰性判定：{@code expires_at} 已过期但状态仍为 ACTIVE 的行
 * 视同 ENDED（无需定时任务收尾，行保留供审计）。</p>
 */
public enum SpecialEventStatus {

    /** 活跃中（全站展示；expires_at 非空且已过期时读取方视同 ENDED） */
    ACTIVE,

    /** 已结束（管理员手动结束 / 新事件触发时自动结束 / 过期惰性判定） */
    ENDED
}
