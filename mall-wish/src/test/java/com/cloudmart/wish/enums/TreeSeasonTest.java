package com.cloudmart.wish.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TreeSeason 单元测试（纯函数：UTC 月份 → 季节映射）。
 */
@DisplayName("TreeSeason 单元测试")
class TreeSeasonTest {

    @Test
    @DisplayName("3/4/5 月 → SPRING")
    void from_springMonths_returnsSpring() {
        assertThat(TreeSeason.from(LocalDate.of(2026, 3, 1))).isEqualTo(TreeSeason.SPRING);
        assertThat(TreeSeason.from(LocalDate.of(2026, 4, 15))).isEqualTo(TreeSeason.SPRING);
        assertThat(TreeSeason.from(LocalDate.of(2026, 5, 31))).isEqualTo(TreeSeason.SPRING);
    }

    @Test
    @DisplayName("6/7/8 月 → SUMMER")
    void from_summerMonths_returnsSummer() {
        assertThat(TreeSeason.from(LocalDate.of(2026, 6, 1))).isEqualTo(TreeSeason.SUMMER);
        assertThat(TreeSeason.from(LocalDate.of(2026, 7, 15))).isEqualTo(TreeSeason.SUMMER);
        assertThat(TreeSeason.from(LocalDate.of(2026, 8, 31))).isEqualTo(TreeSeason.SUMMER);
    }

    @Test
    @DisplayName("9/10/11 月 → AUTUMN")
    void from_autumnMonths_returnsAutumn() {
        assertThat(TreeSeason.from(LocalDate.of(2026, 9, 1))).isEqualTo(TreeSeason.AUTUMN);
        assertThat(TreeSeason.from(LocalDate.of(2026, 10, 15))).isEqualTo(TreeSeason.AUTUMN);
        assertThat(TreeSeason.from(LocalDate.of(2026, 11, 30))).isEqualTo(TreeSeason.AUTUMN);
    }

    @Test
    @DisplayName("12/1/2 月 → WINTER（跨年季节）")
    void from_winterMonths_returnsWinter() {
        assertThat(TreeSeason.from(LocalDate.of(2026, 12, 1))).isEqualTo(TreeSeason.WINTER);
        assertThat(TreeSeason.from(LocalDate.of(2026, 1, 15))).isEqualTo(TreeSeason.WINTER);
        assertThat(TreeSeason.from(LocalDate.of(2026, 2, 28))).isEqualTo(TreeSeason.WINTER);
    }

    @Test
    @DisplayName("全年 12 个月映射完备（无遗漏无重叠）")
    void from_fullYear_allMonthsMapped() {
        // 月份分组：3-5→SPRING 6-8→SUMMER 9-11→AUTUMN 12/1/2→WINTER
        TreeSeason[] expectedByMonth = {
                TreeSeason.WINTER, TreeSeason.WINTER, TreeSeason.SPRING,
                TreeSeason.SPRING, TreeSeason.SPRING, TreeSeason.SUMMER,
                TreeSeason.SUMMER, TreeSeason.SUMMER, TreeSeason.AUTUMN,
                TreeSeason.AUTUMN, TreeSeason.AUTUMN, TreeSeason.WINTER
        };
        for (int month = 1; month <= 12; month++) {
            assertThat(TreeSeason.from(LocalDate.of(2026, month, 15)))
                    .isEqualTo(expectedByMonth[month - 1]);
        }
    }
}
