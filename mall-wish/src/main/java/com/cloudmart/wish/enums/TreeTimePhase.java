package com.cloudmart.wish.enums;

import java.time.LocalTime;

/**
 * 世界生命树时段环境枚举（Sprint 2.2，文档第二章 2. 时间）。
 *
 * <p>时段按用户本地时区计算（文档 Sprint 2.2 验收：跨时区用户按本地
 * 时区而非服务器时区）：06-12 白天 / 12-18 黄昏 / 18-24 夜晚 /
 * 00-06 深夜。四端将本地 UTC 偏移（分钟）作为参数传入，服务端统一
 * 计算保证跨端口径一致。</p>
 */
public enum TreeTimePhase {

    /** 白天（06:00-12:00） */
    DAY,

    /** 黄昏（12:00-18:00） */
    DUSK,

    /** 夜晚（18:00-24:00） */
    NIGHT,

    /** 深夜（00:00-06:00） */
    LATE_NIGHT;

    /**
     * 按本地时刻判定时段。
     *
     * @param localTime 用户本地时刻
     * @return 对应时段枚举
     */
    public static TreeTimePhase from(LocalTime localTime) {
        int hour = localTime.getHour();
        if (hour >= 6 && hour < 12) {
            return DAY;
        }
        if (hour >= 12 && hour < 18) {
            return DUSK;
        }
        if (hour >= 18) {
            return NIGHT;
        }
        return LATE_NIGHT;
    }
}
