package com.cloudmart.wish.enums;

/**
 * 世界生命树天气枚举（Sprint 2.2，文档第二章 2. 天气 + 28.1.2 和风天气 v7）。
 *
 * <p>真实天气来自和风天气 API（{@code now.text} 中文天气现象），
 * 映射规则见 {@link #fromQWeatherText(String)}；API 不可用时降级 SUNNY
 * （文档 28.1.2 降级策略）。RAINBOW 不来自真实天气——仅由情绪联动
 * 状态机触发（文档 2.2 治愈叙事），枚举归属此处是为了与前端渲染
 * 配置表（wish_env_config）共用 code。</p>
 */
public enum TreeWeather {

    /** 晴天（默认/降级值） */
    SUNNY,

    /** 多云/阴 */
    CLOUDY,

    /** 下雨（真实降雨；情绪联动的"下雨"见 TreeEnvironment.RAIN） */
    RAIN,

    /** 下雪 */
    SNOW,

    /** 彩虹（仅情绪联动触发，真实天气 API 不会返回） */
    RAINBOW;

    /**
     * 和风天气现象文本 → 天气枚举。
     *
     * <p>映射规则（和风 v7 {@code now.text} 中文枚举集）：</p>
     * <ul>
     *   <li>晴/阳光 → SUNNY</li>
     *   <li>含"雨"但含"雪"优先归 SNOW（雨夹雪按雪渲染）</li>
     *   <li>含"雪" → SNOW</li>
     *   <li>含"雨" → RAIN</li>
     *   <li>多云/阴/雾/霾/沙尘等其余 → CLOUDY（天空朦胧系）</li>
     *   <li>空/null/未识别 → SUNNY（降级默认，不抛错）</li>
     * </ul>
     *
     * @param text 和风天气现象文本（如"晴""小雨""雨夹雪""多云"）
     * @return 对应天气枚举
     */
    public static TreeWeather fromQWeatherText(String text) {
        if (text == null || text.isBlank()) {
            return SUNNY;
        }
        String normalized = text.trim();
        if (normalized.contains("雪")) {
            return SNOW;
        }
        if (normalized.contains("雨")) {
            return RAIN;
        }
        if (normalized.contains("晴")) {
            return SUNNY;
        }
        return CLOUDY;
    }
}
