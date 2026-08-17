package com.cloudmart.wish.enums;

/**
 * 心愿状态机枚举。
 *
 * <p>合法流转路径（见文档 Sprint 1.5 验收清单）：</p>
 * <ul>
 *   <li>DRAFT → ACTIVE（发布）</li>
 *   <li>ACTIVE → OVERDUE（expected_at 过期 + mall-job 扫描）</li>
 *   <li>ACTIVE → FULFILLING → FULFILLED（还愿流程）</li>
 *   <li>OVERDUE → FULFILLED（过期后仍可还愿，产品规则允许延期完成）</li>
 *   <li>FULFILLED → ARCHIVED（作者归档，仅 FULFILLED 可归档；SPARK 不可归档）</li>
 * </ul>
 * <p>非法流转：DRAFT 不能直接跳 FULFILLED；已 FULFILLED 不能再还愿。</p>
 */
public enum WishStatus {
    /** 草稿 */
    DRAFT,
    /** 已发布/进行中 */
    ACTIVE,
    /** 已过期（expected_at 过期 + mall-job 扫描） */
    OVERDUE,
    /** 还愿中（提交还愿但未完成审核，Sprint 1.5） */
    FULFILLING,
    /** 已还愿 */
    FULFILLED,
    /** 已归档（作者归档，不再公开展示但作者可见） */
    ARCHIVED
}
