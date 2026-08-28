package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.WishFence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 围栏判定算法单元测试（Sprint 3.2 验收：边界/超范围/有效期）。
 */
@DisplayName("围栏判定纯函数")
class FenceJudgeTest {

    private static final double FENCE_LAT = 23.1059;
    private static final double FENCE_LNG = 113.3236;
    private static final double RADIUS = 100.0;

    /** 沿纬度向北偏移指定米数（1° ≈ 111320m） */
    private static double north(double lat, double meters) {
        return lat + meters / 111320.0;
    }

    @Test
    @DisplayName("围栏内：距离 0 < radius → true")
    void inside() {
        assertThat(FenceJudge.isInside(FENCE_LAT, FENCE_LNG, RADIUS, FENCE_LAT, FENCE_LNG)).isTrue();
        assertThat(FenceJudge.isInside(FENCE_LAT, FENCE_LNG, RADIUS, north(FENCE_LAT, 50), FENCE_LNG)).isTrue();
    }

    @Test
    @DisplayName("围栏边界：距离 = radius → true（含等号，验收项）")
    void boundary() {
        assertThat(FenceJudge.isInside(FENCE_LAT, FENCE_LNG, RADIUS, north(FENCE_LAT, RADIUS), FENCE_LNG)).isTrue();
    }

    @Test
    @DisplayName("围栏外：距离 > radius → false")
    void outside() {
        assertThat(FenceJudge.isInside(FENCE_LAT, FENCE_LNG, RADIUS,
                north(FENCE_LAT, RADIUS + 1), FENCE_LNG)).isFalse();
    }

    @Test
    @DisplayName("有效期：now ∈ [valid_from, valid_to] 含端点 → true")
    void effectivePeriod() {
        WishFence fence = new WishFence();
        fence.setIsActive(true);
        fence.setValidFrom(LocalDateTime.of(2026, 8, 1, 0, 0));
        fence.setValidTo(LocalDateTime.of(2026, 8, 31, 23, 59));

        LocalDateTime nowUtc = LocalDateTime.of(2026, 8, 15, 12, 0);
        assertThat(FenceJudge.isEffective(fence, nowUtc)).isTrue();
        // 端点含等号
        assertThat(FenceJudge.isEffective(fence, LocalDateTime.of(2026, 8, 1, 0, 0))).isTrue();
        assertThat(FenceJudge.isEffective(fence, LocalDateTime.of(2026, 8, 31, 23, 59))).isTrue();
        // 界外
        assertThat(FenceJudge.isEffective(fence, LocalDateTime.of(2026, 9, 1, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("状态：is_active=0 → 恒 false；有效期 NULL=不限")
    void activeAndNullPeriod() {
        WishFence inactive = new WishFence();
        inactive.setIsActive(false);
        assertThat(FenceJudge.isEffective(inactive, LocalDateTime.now())).isFalse();

        WishFence activeNoPeriod = new WishFence();
        activeNoPeriod.setIsActive(true);
        assertThat(FenceJudge.isEffective(activeNoPeriod, LocalDateTime.now())).isTrue();
    }
}
