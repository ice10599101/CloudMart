package com.cloudmart.wish.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 树洞情绪聚合器（文档 2.2 气象情绪联动）。
 *
 * <p>算法：滑动窗口内所有 TREE_HOLE 场景 ASSISTANT 记录的 sentiment_score
 * （-100~100 整数，换算为 -1.0~+1.0）按样本年龄做指数衰减加权平均——
 * 权重 {@code w = exp(-λ × 年龄分钟数)}，越新权重越高（文档 2.2：
 * "按 created_at 衰减，越新权重越高"）。</p>
 *
 * <p>纯函数、无副作用，便于单元测试与并发安全。</p>
 */
public final class MoodAggregator {

    /** 单条情绪样本：存储侧整数分数（-100~100）+ 记录时间 */
    public record MoodSample(int sentimentScore, LocalDateTime createdAt) {}

    /** 聚合结果：加权平均分数（无样本时为 null）+ 样本计数 */
    public record MoodAggregate(Double score, int sampleCount) {}

    private MoodAggregator() {}

    /**
     * 计算窗口内聚合情绪分数。
     *
     * @param samples     窗口内样本（调用方负责窗口过滤与非空分数过滤）
     * @param now         当前时间（权重计算基准）
     * @param decayLambda 衰减系数 λ（0 表示不衰减）
     * @return 聚合结果；无有效样本时 score 为 null
     */
    public static MoodAggregate aggregate(List<MoodSample> samples, LocalDateTime now, double decayLambda) {
        if (samples == null || samples.isEmpty()) {
            return new MoodAggregate(null, 0);
        }
        double weightedSum = 0.0;
        double weightSum = 0.0;
        for (MoodSample sample : samples) {
            // 秒级精度计算年龄，避免分钟截断导致相邻样本权重相同
            double ageMinutes = Math.max(0.0, Duration.between(sample.createdAt(), now).toSeconds() / 60.0);
            double weight = decayLambda <= 0 ? 1.0 : Math.exp(-decayLambda * ageMinutes);
            weightedSum += (sample.sentimentScore() / 100.0) * weight;
            weightSum += weight;
        }
        if (weightSum <= 0.0) {
            return new MoodAggregate(null, samples.size());
        }
        double score = clamp(weightedSum / weightSum);
        return new MoodAggregate(score, samples.size());
    }

    /** 限制分数在 [-1.0, 1.0]（TINYINT 存储侧换算本身在范围内，防御异常数据） */
    private static double clamp(double score) {
        return Math.max(-1.0, Math.min(1.0, score));
    }
}
