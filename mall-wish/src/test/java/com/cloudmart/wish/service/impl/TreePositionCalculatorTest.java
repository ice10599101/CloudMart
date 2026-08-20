package com.cloudmart.wish.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TreePositionCalculator 单元测试（纯函数：确定性/值域/精度/分散性）。
 */
@DisplayName("TreePositionCalculator 单元测试")
class TreePositionCalculatorTest {

    private static final double TWO_PI = 2 * Math.PI;

    @Test
    @DisplayName("同 id 恒同值（确定性，果实位置稳定不跳动的前提）")
    void assign_sameId_returnsIdenticalPosition() {
        TreePositionCalculator.TreePosition first = TreePositionCalculator.assign(123456789L);
        TreePositionCalculator.TreePosition second = TreePositionCalculator.assign(123456789L);

        assertThat(first.theta()).isEqualByComparingTo(second.theta());
        assertThat(first.phi()).isEqualByComparingTo(second.phi());
    }

    @Test
    @DisplayName("theta ∈ [0, 2π)、phi ∈ (0, π]（球面坐标合法值域）")
    void assign_valueRange_withinSphereBounds() {
        for (long id = 1; id <= 2000; id++) {
            TreePositionCalculator.TreePosition position = TreePositionCalculator.assign(id);
            assertThat(position.theta().doubleValue()).isGreaterThanOrEqualTo(0.0);
            assertThat(position.theta().doubleValue()).isLessThan(TWO_PI);
            assertThat(position.phi().doubleValue()).isGreaterThan(0.0);
            assertThat(position.phi().doubleValue()).isLessThanOrEqualTo(Math.PI);
        }
    }

    @Test
    @DisplayName("坐标按 7 位小数固化（与 V9 DECIMAL(9,7) 精度对齐）")
    void assign_scale_isSevenDecimals() {
        TreePositionCalculator.TreePosition position = TreePositionCalculator.assign(42L);

        assertThat(position.theta().scale()).isEqualTo(7);
        assertThat(position.phi().scale()).isEqualTo(7);
    }

    @Test
    @DisplayName("不同 id 坐标充分分散（黄金角散列无碰撞堆积）")
    void assign_differentIds_produceDistinctPositions() {
        Set<String> distinct = new HashSet<>();
        for (long id = 1; id <= 1000; id++) {
            TreePositionCalculator.TreePosition position = TreePositionCalculator.assign(id);
            distinct.add(position.theta().toPlainString() + ":" + position.phi().toPlainString());
        }

        // 1000 个 id 至少 990 个互不相同（散列质量下界，留 1% 容差）
        assertThat(distinct).hasSizeGreaterThan(990);
    }

    @Test
    @DisplayName("雪花量级大 id 正常计算（long 溢出回绕不异常）")
    void assign_snowflakeId_computesWithoutError() {
        long snowflakeId = 1940123456789012480L;

        TreePositionCalculator.TreePosition position = TreePositionCalculator.assign(snowflakeId);

        assertThat(position.theta()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(TWO_PI));
        assertThat(position.phi()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(Math.PI));
        // 与同 id 重复计算一致（大 id 同样确定）
        assertThat(TreePositionCalculator.assign(snowflakeId).theta())
                .isEqualByComparingTo(position.theta());
    }

    @Test
    @DisplayName("批量抽样经度分布均匀（四象限各有覆盖，极区不堆积）")
    void assign_batchSample_coversAllQuadrants() {
        Set<Integer> quadrants = new HashSet<>();
        for (long id = 1; id <= 400; id++) {
            double theta = TreePositionCalculator.assign(id).theta().doubleValue();
            quadrants.add((int) (theta / (Math.PI / 2)));
        }

        assertThat(quadrants).containsExactlyInAnyOrder(0, 1, 2, 3);
    }
}
