package com.cloudmart.wish.enums;

/**
 * 星光流水来源枚举。
 *
 * <p>对应 {@code wish_resource_log.source} 字段，标识星光变化的业务来源。</p>
 */
public enum ResourceLogSource {
    /** 每日签到（+5） */
    SIGNIN,
    /** 被点亮（+1） */
    LIGHTED,
    /** 被同求（+2，文档 6.1 节；V1 表注释来源清单遗漏，字段为 VARCHAR 无需迁移） */
    SAME_WISHED,
    /** 打卡（+3） */
    CHECKIN,
    /** 还愿（奖励） */
    FULFILL,
    /** 点亮他人（-2） */
    LIGHT_OTHER,
    /** 匿名星光（-5，Sprint 2.6） */
    ANON_STAR,
    /** 虚拟工坊兑换（Sprint 3.x） */
    EXCHANGE
}
