package com.cloudmart.wish.enums;

/**
 * 环境配置分类（Sprint 2.2，wish_env_config.category）。
 *
 * <p>仅用于管理端分组展示；渲染优先级由 {@code priority} 数值决定，
 * 与本分类无强绑定（新增"中秋"等自定义分类环境亦不改代码）。</p>
 */
public enum EnvCategory {

    /** 天气环境（和风天气 API / 情绪联动） */
    WEATHER,

    /** 季节环境（mall-job 每日扫描落库） */
    SEASON,

    /** 时段环境（按用户本地时区计算） */
    TIME,

    /** 特殊事件环境（管理员触发全站同步） */
    SPECIAL_EVENT
}
