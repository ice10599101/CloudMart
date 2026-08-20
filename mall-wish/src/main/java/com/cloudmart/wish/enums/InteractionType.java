package com.cloudmart.wish.enums;

/**
 * 心愿互动类型枚举。
 *
 * <p>限频规则（见文档第四章 4.1 频率限制）：</p>
 * <ul>
 *   <li>LIGHT：单用户每日 ≤ 50 次，可重复（不受唯一约束）</li>
 *   <li>SAME_WISH：单用户每日 ≤ 10 次，同一愿望仅可 1 次（唯一索引保证）</li>
 *   <li>BLESS：单用户对同一心愿每日 ≤ 1 次，每日总量 ≤ 20 次</li>
 *   <li>ANON_STAR：单用户每日 ≤ 3 次，同一愿望仅 1 次（唯一索引保证）</li>
 * </ul>
 */
public enum InteractionType {
    /** 点亮（消耗 2 星光，可重复） */
    LIGHT,
    /** 同求（唯一，不可重复） */
    SAME_WISH,
    /** 祝福（带文字，每愿望每日 1 次） */
    BLESS,
    /** 匿名星光（消耗 5 星光，每日 ≤ 3 次，同一愿望 1 次） */
    ANON_STAR
}
