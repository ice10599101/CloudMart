package com.cloudmart.wish.enums;

import java.time.LocalDate;

/**
 * 世界生命树季节枚举（Sprint 2.1，文档第二章 2.）。
 *
 * <p>季节按服务器 UTC 日期判定：3-5 月春 / 6-8 月夏 / 9-11 月秋 / 12-2 月冬
 * （与 Sprint 2.2 落库口径一致）。Sprint 2.1 阶段由本枚举实时计算，
 * Sprint 2.2 起 mall-job 每日 00:00 扫描写入 {@code wish_world_tree_state.season}
 * 字段后切换为读表。</p>
 */
public enum TreeSeason {
    /** 春季（3-5 月，嫩绿花瓣） */
    SPRING,
    /** 夏季（6-8 月，绿叶阳光斑驳） */
    SUMMER,
    /** 秋季（9-11 月，金黄落叶） */
    AUTUMN,
    /** 冬季（12-2 月，枯枝雪花） */
    WINTER;

    /**
     * 按 UTC 日期判定季节。
     *
     * @param date UTC 日期
     * @return 对应季节枚举
     */
    public static TreeSeason from(LocalDate date) {
        return switch (date.getMonthValue()) {
            case 3, 4, 5 -> SPRING;
            case 6, 7, 8 -> SUMMER;
            case 9, 10, 11 -> AUTUMN;
            default -> WINTER;
        };
    }
}
