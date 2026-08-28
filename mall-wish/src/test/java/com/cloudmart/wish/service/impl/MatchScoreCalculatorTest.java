package com.cloudmart.wish.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 匹配评分纯函数测试（Sprint 2.6 验收：权重配置/边界值/空数据）。
 */
@DisplayName("匹配评分纯函数")
class MatchScoreCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 12, 0);

    @Test
    @DisplayName("活跃度：null（从未活跃）得 0（冷启动依赖关键词优先）")
    void activityScoreNullIsZero() {
        assertThat(MatchScoreCalculator.activityScore(null, NOW)).isZero();
    }

    @Test
    @DisplayName("活跃度：刚活跃≈1，7 天≈0.5（半衰期），28 天≈0.06")
    void activityScoreDecayHalfLife() {
        double justNow = MatchScoreCalculator.activityScore(NOW.minusHours(1), NOW);
        double sevenDays = MatchScoreCalculator.activityScore(NOW.minusDays(7), NOW);
        double twentyEightDays = MatchScoreCalculator.activityScore(NOW.minusDays(28), NOW);

        assertThat(justNow).isCloseTo(1.0, within(0.05));
        assertThat(sevenDays).isCloseTo(0.5, within(0.02));
        assertThat(twentyEightDays).isLessThan(0.1);
    }

    @Test
    @DisplayName("活跃度：未来时间（时钟回拨）钳制为 1")
    void activityScoreFutureClamped() {
        assertThat(MatchScoreCalculator.activityScore(NOW.plusDays(3), NOW)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("关键词：查询词精确命中得 1，未命中得 0")
    void keywordScoreExactMatch() {
        assertThat(MatchScoreCalculator.keywordScore("看极光", null, "看极光")).isEqualTo(1.0);
        assertThat(MatchScoreCalculator.keywordScore("减肥", null, "看极光")).isZero();
    }

    @Test
    @DisplayName("关键词：互为包含（半衰期词干/标签超集）也算命中")
    void keywordScorePartialContains() {
        assertThat(MatchScoreCalculator.keywordScore("想去看极光", null, "看极光")).isEqualTo(1.0);
        assertThat(MatchScoreCalculator.keywordScore(null, List.of("旅行", "看极光打卡"), "看极光")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("关键词：空查询 + 空标签（冷启动）得 0")
    void keywordScoreColdStart() {
        assertThat(MatchScoreCalculator.keywordScore(null, List.of(), "看极光")).isZero();
        assertThat(MatchScoreCalculator.keywordScore(null, null, "看极光")).isZero();
        assertThat(MatchScoreCalculator.keywordScore("", null, "")).isZero();
    }

    @Test
    @DisplayName("加权总分：默认权重 0.4/0.3/0.3 下全命中得 1")
    void scoreAllHit() {
        var breakdown = MatchScoreCalculator.score(1.0, 1.0, 1.0, 0.4, 0.3, 0.3, "看极光");
        assertThat(breakdown.total()).isCloseTo(1.0, within(0.001));
        assertThat(breakdown.reasons()).contains("你们都想看极光", "你们可能是同城伙伴", "组员们最近也在坚持打卡");
    }

    @Test
    @DisplayName("加权总分：仅关键词命中得 0.4（关键词优先）")
    void scoreKeywordOnly() {
        var breakdown = MatchScoreCalculator.score(1.0, 0.0, 0.0, 0.4, 0.3, 0.3, "看极光");
        assertThat(breakdown.total()).isCloseTo(0.4, within(0.001));
    }

    @Test
    @DisplayName("加权总分：权重和不为 1 时按比例归一（配置容错）")
    void scoreNormalizesWeights() {
        var breakdown = MatchScoreCalculator.score(1.0, 0.0, 0.0, 0.8, 0.6, 0.6, "看极光");
        // 归一后 keyword 权重 = 0.8/2.0 = 0.4
        assertThat(breakdown.total()).isCloseTo(0.4, within(0.001));
    }

    @Test
    @DisplayName("加权总分：权重全为 0（配置错误）退化为等权平均，不抛异常")
    void scoreZeroWeightsFallback() {
        var breakdown = MatchScoreCalculator.score(1.0, 0.0, 0.5, 0.0, 0.0, 0.0, "看极光");
        assertThat(breakdown.total()).isCloseTo(0.5, within(0.001));
    }

    @Test
    @DisplayName("相似度说明：无命中维度时给兜底文案（三端一致由 API 下发）")
    void scoreFallbackReason() {
        var breakdown = MatchScoreCalculator.score(0.0, 0.0, 0.2, 0.4, 0.3, 0.3, "看极光");
        assertThat(breakdown.reasons()).containsExactly("同愿热度推荐");
    }

    @Test
    @DisplayName("小组活跃度：成员列表为空得 0（空数据边界）")
    void groupActivityEmpty() {
        assertThat(MatchScoreCalculator.groupActivityScore(List.of(), NOW)).isZero();
        assertThat(MatchScoreCalculator.groupActivityScore(null, NOW)).isZero();
    }

    @Test
    @DisplayName("小组活跃度：成员活跃度取平均")
    void groupActivityAverage() {
        var memberTimes = List.of(NOW.minusHours(1), NOW.minusDays(7));
        double avg = MatchScoreCalculator.groupActivityScore(memberTimes, NOW);
        double expected = (1.0 + 0.5) / 2.0;
        assertThat(avg).isCloseTo(expected, within(0.05));
    }
}
