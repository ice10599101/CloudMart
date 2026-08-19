package com.cloudmart.wish.enums;

/**
 * 世界生命树环境变更来源（观测与审计用途，不参与状态机判定）。
 */
public enum TreeEnvSource {

    /** 初始化（单行状态首次创建） */
    INIT,

    /** 情绪阈值触发下雨（SUNNY/RAINBOW → RAIN） */
    MOOD_RAIN,

    /** 下雨持续（扫描时情绪仍低于阈值，保持 RAIN 并刷新最短持续基准外的扫描时间） */
    MOOD_RAIN_RENEW,

    /** 情绪好转触发彩虹（RAIN/SUNNY → RAINBOW） */
    MOOD_RAINBOW,

    /** BLESS 祝福突增触发彩虹（文档 2.2：收到他人祝福触发彩虹，可打断下雨） */
    BLESS_BURST_RAINBOW,

    /** 彩虹到期回落（RAINBOW → 扫描复评结果） */
    RAINBOW_EXPIRED,

    /** 情绪回升恢复晴天（RAIN 最短持续期满且不再低落） */
    MOOD_RECOVER
}
