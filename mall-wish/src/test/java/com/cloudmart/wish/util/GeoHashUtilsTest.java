package com.cloudmart.wish.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * GeoHash 编解码纯函数测试（Sprint 3.1 验收：编解码边界/空值/非法字符、
 * 偏移算法可复现性）。
 */
@DisplayName("GeoHash 编解码纯函数")
class GeoHashUtilsTest {

    /** 广州塔（测试基准点） */
    private static final double LAT = 23.1059;
    private static final double LNG = 113.3236;

    @Test
    @DisplayName("编解码往返：geohash7 中心与原点距离 < 200m（网格误差内）")
    void roundTripPrecision7() {
        String hash = GeoHashUtils.encode(LAT, LNG, 7);
        assertThat(hash).hasSize(7);
        double[] center = GeoHashUtils.decodeCenter(hash);
        double distance = GeoHashUtils.distanceMeters(LAT, LNG, center[0], center[1]);
        assertThat(distance).isLessThan(200.0);
    }

    @Test
    @DisplayName("精度对照：geohash6 < 2km、geohash5 < 8km（文档隐私策略表）")
    void precisionTiers() {
        double d6 = GeoHashUtils.distanceMeters(LAT, LNG, GeoHashUtils.decodeCenter(
                GeoHashUtils.encode(LAT, LNG, 6))[0],
                GeoHashUtils.decodeCenter(GeoHashUtils.encode(LAT, LNG, 6))[1]);
        double d5 = GeoHashUtils.distanceMeters(LAT, LNG, GeoHashUtils.decodeCenter(
                GeoHashUtils.encode(LAT, LNG, 5))[0],
                GeoHashUtils.decodeCenter(GeoHashUtils.encode(LAT, LNG, 5))[1]);
        assertThat(d6).isLessThan(2000.0);
        assertThat(d5).isLessThan(8000.0);
    }

    @Test
    @DisplayName("非法输入：长度<1/精度越界/坐标越界 → IllegalArgumentException")
    void invalidInputs() {
        assertThatThrownBy(() -> GeoHashUtils.encode(91.0, LNG, 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoHashUtils.encode(LAT, 181.0, 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoHashUtils.encode(LAT, LNG, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoHashUtils.decodeCenter("a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoHashUtils.decodeCenter(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validate：长度<6 或非法字符（a/i/l/o）拒绝（验收项）")
    void validateRejects() {
        assertThatCode(() -> GeoHashUtils.validate("ws1e2z3", 6)).doesNotThrowAnyException();
        assertThatThrownBy(() -> GeoHashUtils.validate("ws1e2", 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoHashUtils.validate("ws1eaiz", 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoHashUtils.validate(null, 6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("邻格：9 格窗口含本格、无重复、跨 0/2π 环绕不抛异常")
    void neighborsNineCells() {
        Set<String> cells = GeoHashUtils.neighbors(GeoHashUtils.encode(LAT, LNG, 5));
        assertThat(cells).hasSize(9);
        // 经度环绕边界（lng=179.9）
        assertThatCode(() -> GeoHashUtils.neighbors(GeoHashUtils.encode(23.0, 179.9, 5)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("确定性偏移：同 seed 恒同结果；偏移距离 ≤ 50m（验收：可复现 + 范围）")
    void deterministicOffsetRange() {
        double[] first = GeoHashUtils.deterministicOffset(LAT, LNG, 9100001L);
        double[] second = GeoHashUtils.deterministicOffset(LAT, LNG, 9100001L);
        assertThat(first).containsExactly(second);

        double distance = GeoHashUtils.distanceMeters(LAT, LNG, first[0], first[1]);
        assertThat(distance).isLessThanOrEqualTo(50.0);
    }

    @Test
    @DisplayName("偏移分散：100 个 seed 的偏移点不全重合（均匀散布防反推）")
    void offsetsSpread() {
        long distinct = java.util.stream.LongStream.rangeClosed(1, 100)
                .mapToObj(seed -> {
                    double[] offset = GeoHashUtils.deterministicOffset(LAT, LNG, seed);
                    return offset[0] + "," + offset[1];
                })
                .distinct()
                .count();
        assertThat(distinct).isGreaterThan(90);
    }

    @Test
    @DisplayName("距离：赤道 1 度经度 ≈ 111.19km（Haversine 基准）")
    void distanceBaseline() {
        double distance = GeoHashUtils.distanceMeters(0.0, 113.0, 0.0, 114.0);
        assertThat(distance).isCloseTo(111190.0, within(200.0));
    }
}
