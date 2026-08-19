package com.cloudmart.wish.enums;

/**
 * 世界生命树环境状态（文档 2.2 / Sprint 2.2）。
 *
 * <p>当前仅落地情绪联动三态；季节（SPRING/SUMMER/AUTUMN/WINTER）、天气、
 * 特殊事件（METEOR_SHOWER 等）由 Sprint 2.2 其余部分扩展。DB 侧为
 * VARCHAR 存储，枚举新增值无需变更表结构。</p>
 */
public enum TreeEnvironment {

    /** 默认晴朗（无情绪触发或情绪回升后恢复） */
    SUNNY,

    /** 下雨：树洞负面情绪累积（mood &lt; -0.6，文档 2.2） */
    RAIN,

    /** 彩虹：情绪好转（mood &gt; +0.3）或 BLESS 祝福突增触发，持续 15 分钟 */
    RAINBOW
}
