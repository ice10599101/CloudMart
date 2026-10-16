package com.cloudmart.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BASE-01：身份 ID 宽松解析——字符串（统一字符串序列化）/数字（安全范围/历史数据）
 * 两种形态均可解析；非法形态返回 null 不抛异常。
 */
@DisplayName("Ids 身份 ID 宽松解析")
class IdsTest {

    @Test
    @DisplayName("数字形态：Integer/Long 直接取值（含超安全范围 Long）")
    void numberForms() {
        assertThat(Ids.parse(42)).isEqualTo(42L);
        assertThat(Ids.parse(9_007_199_254_740_993L)).isEqualTo(9_007_199_254_740_993L);
    }

    @Test
    @DisplayName("字符串形态：十进制字符串（含两端空白）解析为 Long，字节无损")
    void stringForms() {
        assertThat(Ids.parse("9007199254740993")).isEqualTo(9_007_199_254_740_993L);
        assertThat(Ids.parse(" 123 ")).isEqualTo(123L);
    }

    @Test
    @DisplayName("非法形态：null/空串/负数/小数/非数字/超长 → null（不抛异常）")
    void invalidForms() {
        assertThat(Ids.parse(null)).isNull();
        assertThat(Ids.parse("")).isNull();
        assertThat(Ids.parse("  ")).isNull();
        assertThat(Ids.parse("-1")).isNull();
        assertThat(Ids.parse("1.5")).isNull();
        assertThat(Ids.parse("12a")).isNull();
        assertThat(Ids.parse("99999999999999999999")).isNull();   // 20 位
        assertThat(Ids.parse("9223372036854775808")).isNull();    // 19 位但超 Long 上限
    }
}
