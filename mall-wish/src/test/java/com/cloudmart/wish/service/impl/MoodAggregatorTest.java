package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.service.impl.MoodAggregator.MoodAggregate;
import com.cloudmart.wish.service.impl.MoodAggregator.MoodSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * MoodAggregator 单元测试（文档 2.2：时间衰减加权平均，越新权重越高）。
 */
@DisplayName("MoodAggregator 单元测试")
class MoodAggregatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 12, 0);
    /** 默认配置 λ=0.0231（1 小时衰减至 25%） */
    private static final double LAMBDA = 0.0231;

    @Nested
    @DisplayName("aggregate - 窗口聚合")
    class AggregateTests {

        @Test
        @DisplayName("空样本：score=null, sampleCount=0")
        void emptySamples_returnsNullScore() {
            MoodAggregate result = MoodAggregator.aggregate(Collections.emptyList(), NOW, LAMBDA);
            assertThat(result.score()).isNull();
            assertThat(result.sampleCount()).isZero();
        }

        @Test
        @DisplayName("null 样本列表：score=null 容错")
        void nullSamples_returnsNullScore() {
            MoodAggregate result = MoodAggregator.aggregate(null, NOW, LAMBDA);
            assertThat(result.score()).isNull();
            assertThat(result.sampleCount()).isZero();
        }

        @Test
        @DisplayName("单样本：score=存储分数/100 换算")
        void singleSample_convertsScale() {
            MoodAggregate result = MoodAggregator.aggregate(
                    List.of(new MoodSample(-60, NOW)), NOW, LAMBDA);
            assertThat(result.score()).isEqualTo(-0.6);
            assertThat(result.sampleCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("同时刻双样本：等权重简单平均")
        void sameAgeSamples_simpleAverage() {
            MoodAggregate result = MoodAggregator.aggregate(
                    List.of(new MoodSample(80, NOW), new MoodSample(40, NOW)), NOW, LAMBDA);
            assertThat(result.score()).isCloseTo(0.6, within(1e-9));
        }

        @Test
        @DisplayName("时间衰减：新样本权重高于旧样本（旧-100/新+100 → 正分）")
        void newerSampleWeighsMore() {
            MoodAggregate result = MoodAggregator.aggregate(
                    List.of(new MoodSample(-100, NOW.minusMinutes(60)),
                            new MoodSample(100, NOW)), NOW, LAMBDA);
            // 新样本权重 1.0，旧样本 ≈ exp(-0.0231×60) ≈ 0.25
            // → (100×1.0 - 100×0.25) / 1.25 = 0.6
            assertThat(result.score()).isPositive();
            assertThat(result.score()).isCloseTo(0.6, within(0.01));
        }

        @Test
        @DisplayName("λ=0：不衰减，退化为简单平均")
        void zeroLambda_noDecay() {
            MoodAggregate result = MoodAggregator.aggregate(
                    List.of(new MoodSample(-100, NOW.minusMinutes(59)),
                            new MoodSample(100, NOW)), NOW, 0.0);
            assertThat(result.score()).isZero();
        }

        @Test
        @DisplayName("未来时间戳（时钟偏差）：权重钳制为最新，不产生放大")
        void futureTimestamp_clampedToFullWeight() {
            MoodAggregate result = MoodAggregator.aggregate(
                    List.of(new MoodSample(-50, NOW.plusMinutes(5))), NOW, LAMBDA);
            assertThat(result.score()).isEqualTo(-0.5);
        }

        @Test
        @DisplayName("极端分数边界：-100~100 全量样本 clamp 在 [-1,1]")
        void extremeScores_clamped() {
            MoodAggregate allNegative = MoodAggregator.aggregate(
                    List.of(new MoodSample(-100, NOW), new MoodSample(-100, NOW)), NOW, LAMBDA);
            MoodAggregate allPositive = MoodAggregator.aggregate(
                    List.of(new MoodSample(100, NOW), new MoodSample(100, NOW)), NOW, LAMBDA);
            assertThat(allNegative.score()).isEqualTo(-1.0);
            assertThat(allPositive.score()).isEqualTo(1.0);
        }
    }
}
