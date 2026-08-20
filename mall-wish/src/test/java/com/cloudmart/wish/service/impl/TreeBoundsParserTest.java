package com.cloudmart.wish.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TreeBoundsParser 单元测试（纯函数：解析规则/值域校验/环绕窗口/异常兜底）。
 *
 * <p>文档验收口径："bounds 参数异常（负数/超范围）→ 默认值兜底，不报错"
 * —— 所有无效输入均返回 empty 退化为全量分页。</p>
 */
@DisplayName("TreeBoundsParser 单元测试")
class TreeBoundsParserTest {

    @Test
    @DisplayName("四参数全空 → 无视口过滤（默认兜底全量分页）")
    void parse_allNull_returnsEmpty() {
        assertThat(TreeBoundsParser.parse(null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("部分提供（1-3 个）→ 整组忽略（半过滤会产生不可预期部分结果）")
    void parse_partialParams_returnsEmpty() {
        assertThat(TreeBoundsParser.parse(0.5, null, 0.0, 1.57)).isEmpty();
        assertThat(TreeBoundsParser.parse(null, 1.5, 0.0, 1.57)).isEmpty();
        assertThat(TreeBoundsParser.parse(0.5, 1.5, null, 1.57)).isEmpty();
        assertThat(TreeBoundsParser.parse(0.5, 1.5, 0.0, null)).isEmpty();
        assertThat(TreeBoundsParser.parse(0.5, 1.5, null, null)).isEmpty();
        assertThat(TreeBoundsParser.parse(0.5, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("合法非环绕视口 → 正常解析，wrapTheta=false")
    void parse_validNonWrapping_returnsBounds() {
        Optional<TreeBoundsParser.TreeBounds> bounds =
                TreeBoundsParser.parse(0.5, 1.5, 0.0, 1.57);

        assertThat(bounds).isPresent();
        assertThat(bounds.get().minPhi()).isEqualTo(0.5);
        assertThat(bounds.get().maxPhi()).isEqualTo(1.5);
        assertThat(bounds.get().minTheta()).isEqualTo(0.0);
        assertThat(bounds.get().maxTheta()).isEqualTo(1.57);
        assertThat(bounds.get().wrapTheta()).isFalse();
    }

    @Test
    @DisplayName("minLng > maxLng → 合法环绕窗口（跨 0/2π 经度），wrapTheta=true")
    void parse_wrappingWindow_returnsBoundsWithWrapFlag() {
        Optional<TreeBoundsParser.TreeBounds> bounds =
                TreeBoundsParser.parse(0.5, 1.5, 5.5, 0.78);

        assertThat(bounds).isPresent();
        assertThat(bounds.get().minTheta()).isEqualTo(5.5);
        assertThat(bounds.get().maxTheta()).isEqualTo(0.78);
        assertThat(bounds.get().wrapTheta()).isTrue();
    }

    @Test
    @DisplayName("边界值合法：lat=0/π、lng=0/2π（含端点）")
    void parse_boundaryValues_valid() {
        assertThat(TreeBoundsParser.parse(0.0, Math.PI, 0.0, 2 * Math.PI)).isPresent();
        assertThat(TreeBoundsParser.parse(0.0, Math.PI, 2 * Math.PI, 0.0)).isPresent();
    }

    @Test
    @DisplayName("纬度负数或超 [0,π] → 整组忽略")
    void parse_latitudeOutOfRange_returnsEmpty() {
        assertThat(TreeBoundsParser.parse(-0.1, 1.5, 0.0, 1.57)).isEmpty();
        assertThat(TreeBoundsParser.parse(0.5, Math.PI + 0.01, 0.0, 1.57)).isEmpty();
    }

    @Test
    @DisplayName("经度负数或超 [0,2π] → 整组忽略")
    void parse_longitudeOutOfRange_returnsEmpty() {
        assertThat(TreeBoundsParser.parse(0.5, 1.5, -0.01, 1.57)).isEmpty();
        assertThat(TreeBoundsParser.parse(0.5, 1.5, 0.0, 2 * Math.PI + 0.01)).isEmpty();
        assertThat(TreeBoundsParser.parse(0.5, 1.5, 5.0, 2 * Math.PI)).isPresent();
    }

    @Test
    @DisplayName("minLat ≥ maxLat（纬度不环绕，零/负宽窗口）→ 整组忽略")
    void parse_minLatGreaterOrEqualToMaxLat_returnsEmpty() {
        assertThat(TreeBoundsParser.parse(1.5, 1.5, 0.0, 1.57)).isEmpty();
        assertThat(TreeBoundsParser.parse(1.5, 0.5, 0.0, 1.57)).isEmpty();
    }

    @Test
    @DisplayName("minLng == maxLng（零宽经度窗口）→ 整组忽略")
    void parse_zeroWidthLongitude_returnsEmpty() {
        assertThat(TreeBoundsParser.parse(0.5, 1.5, 1.57, 1.57)).isEmpty();
    }
}
