package com.cloudmart.common.util;

/**
 * BASE-01：身份 ID 的宽松解析。
 *
 * <p>统一字符串序列化（{@code JsSafeLongSerializer}）下，雪花 ID 在 JSON 中以
 * 十进制字符串到达；安全范围内的 Long（历史数据、小整数种子）仍为数字形态。
 * 任何"只接受 Number"的强转/instanceof 判断都会在字符串形态下失效
 * （ClassCastException、误判缺失、静默丢事件）。本工具统一接受两种形态。</p>
 *
 * <p>不抛异常：非法形态返回 {@code null}，由调用方决定语义（拒绝消费/跳过/报错），
 * 避免把数据问题变成不可观测的崩溃。</p>
 */
public final class Ids {

    private Ids() {
    }

    /**
     * 数字或 1..19 位十进制字符串 → Long；null/空串/非数字/超长返回 null。
     */
    public static Long parse(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 19
                    && trimmed.chars().allMatch(Character::isDigit)) {
                try {
                    return Long.parseLong(trimmed);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
