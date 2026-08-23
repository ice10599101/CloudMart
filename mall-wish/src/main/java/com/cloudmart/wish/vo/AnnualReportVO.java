package com.cloudmart.wish.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 年度报告 VO（GET /ai/annual-report，文档 2.11）。
 *
 * <p>聚合该用户指定年度的愿望/打卡/成长数据；growthSummary 由 AI 生成
 * （未同意 AI 协议或 AI 失败时为模板降级文案）。报告不持久化，
 * 结果缓存于 Redis 任务记录（TTL 由 wish_ai_config annual_report.ttl_hours 配置）。</p>
 *
 * @param year             报告年度
 * @param fulfilledCount   该年实现心愿数
 * @param totalCheckinDays 该年打卡天数（去重）
 * @param growthSummary    成长总结（AI 生成或模板降级）
 * @param milestones       成长里程碑（该年审核通过的记录，最多 10 条）
 * @param topCategories    热门心愿分类 TOP 3
 */
public record AnnualReportVO(
        int year,
        int fulfilledCount,
        int totalCheckinDays,
        String growthSummary,
        List<Milestone> milestones,
        List<TopCategory> topCategories) {

    /**
     * 成长里程碑。
     */
    public record Milestone(LocalDate date, String title, String description) {
    }

    /**
     * 热门心愿分类。
     */
    public record TopCategory(String name, int count) {
    }
}
