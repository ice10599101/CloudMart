package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.entity.WishCheckin;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.GrowthRecordType;
import com.cloudmart.wish.util.ContentCipher;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.repository.WishCheckinMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AnnualReportService;
import com.cloudmart.wish.vo.AnnualReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 年度报告服务实现（Sprint 2.5，文档 2.11）。
 *
 * <p>同步聚合统计（索引查询，提交 P95 &lt; 500ms）+ 异步 AI 生成
 * growthSummary（AnnualReportGenerator）；缓存命中直接返回 AI 完整版。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualReportServiceImpl implements AnnualReportService {

    /** 成长里程碑数量上限 */
    private static final int MILESTONE_LIMIT = 10;

    /** 热门分类数量上限 */
    private static final int TOP_CATEGORY_LIMIT = 3;

    /** 成长记录 description 截断长度 */
    private static final int DESCRIPTION_MAX_LENGTH = 100;

    private final WishMapper wishMapper;
    private final ContentCipher contentCipher;
    private final WishCheckinMapper checkinMapper;
    private final WishGrowthRecordMapper growthRecordMapper;
    private final WishCategoryMapper categoryMapper;
    private final AnnualReportGenerator reportGenerator;

    @Override
    public AnnualReportVO getOrGenerateReport(Long userId, int year) {
        int currentYear = LocalDate.now().getYear();
        if (year < 2020 || year > currentYear) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "报告年度须在 2020-" + currentYear + " 之间");
        }

        // 缓存命中：返回 AI 完整版（含 growthSummary）
        AnnualReportVO cached = reportGenerator.readCache(userId, year);
        if (cached != null) {
            return cached;
        }

        AnnualReportVO report = aggregateStatistics(userId, year);
        if (isAiWorthGenerating(report)) {
            // 幂等提交：同一用户同一年至多一个进行中任务
            if (reportGenerator.tryLock(userId, year)) {
                reportGenerator.generateAsync(userId, year, report);
            }
        }
        return report;
    }

    /**
     * 是否值得提交 AI 任务：全年完全无数据时跳过（AI 无内容可总结，
     * 模板文案已表达"新的一年从许愿开始"）。
     */
    private boolean isAiWorthGenerating(AnnualReportVO report) {
        return report.fulfilledCount() > 0 || report.totalCheckinDays() > 0
                || !report.milestones().isEmpty();
    }

    /**
     * 同步聚合该年统计数据（growthSummary 为模板降级文案）。
     */
    private AnnualReportVO aggregateStatistics(Long userId, int year) {
        LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime nextYearStart = yearStart.plusYears(1);

        int fulfilledCount = countFulfilledWishes(userId, yearStart, nextYearStart);
        int checkinDays = countCheckinDays(userId, yearStart, nextYearStart);
        List<AnnualReportVO.Milestone> milestones =
                loadMilestones(userId, yearStart, nextYearStart);
        List<AnnualReportVO.TopCategory> topCategories =
                loadTopCategories(userId, yearStart, nextYearStart);
        String templateSummary = buildTemplateSummary(year, fulfilledCount, checkinDays);

        return new AnnualReportVO(year, fulfilledCount, checkinDays, templateSummary,
                milestones, topCategories);
    }

    private int countFulfilledWishes(Long userId, LocalDateTime start, LocalDateTime end) {
        Long count = wishMapper.selectCount(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getUserId, userId)
                .eq(Wish::getStatus, WishStatus.FULFILLED)
                .ge(Wish::getFulfilledAt, start)
                .lt(Wish::getFulfilledAt, end));
        return count != null ? count.intValue() : 0;
    }

    /**
     * 打卡天数（去重）：拉取该年打卡记录的日期列后 Java distinct
     * （单用户一年最多 366 个不同日期，列数据量可控）。
     */
    private int countCheckinDays(Long userId, LocalDateTime start, LocalDateTime end) {
        List<WishCheckin> records = checkinMapper.selectList(new LambdaQueryWrapper<WishCheckin>()
                .select(WishCheckin::getCheckinDate)
                .eq(WishCheckin::getUserId, userId)
                .ge(WishCheckin::getCheckinDate, start.toLocalDate())
                .lt(WishCheckin::getCheckinDate, end.toLocalDate()));
        return (int) records.stream().map(WishCheckin::getCheckinDate).distinct().count();
    }

    private List<AnnualReportVO.Milestone> loadMilestones(Long userId,
                                                          LocalDateTime start, LocalDateTime end) {
        List<WishGrowthRecord> records = growthRecordMapper.selectList(
                new LambdaQueryWrapper<WishGrowthRecord>()
                        .eq(WishGrowthRecord::getUserId, userId)
                        .eq(WishGrowthRecord::getAuditStatus, AuditStatus.APPROVED)
                        .eq(WishGrowthRecord::getIsVisible, true)
                        .ge(WishGrowthRecord::getCreatedAt, start)
                        .lt(WishGrowthRecord::getCreatedAt, end)
                        .orderByDesc(WishGrowthRecord::getCreatedAt)
                        .last("LIMIT " + MILESTONE_LIMIT));
        return records.stream()
                .map(record -> new AnnualReportVO.Milestone(
                        record.getCreatedAt().toLocalDate(),
                        milestoneTitle(record.getType()),
                        truncate(contentCipher.decryptGrowth(
                                GrowthRecordType.DIARY == record.getType(), record.getContent()))))
                .toList();
    }

    /**
     * 热门分类 TOP 3：该年创建的心愿按分类计数（Java 聚合，单用户年心愿量可控）。
     */
    private List<AnnualReportVO.TopCategory> loadTopCategories(Long userId,
                                                               LocalDateTime start, LocalDateTime end) {
        List<Wish> wishes = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                .select(Wish::getCategoryId)
                .eq(Wish::getUserId, userId)
                .ge(Wish::getCreatedAt, start)
                .lt(Wish::getCreatedAt, end)
                .isNotNull(Wish::getCategoryId));
        Map<Long, Long> categoryCounts = wishes.stream()
                .collect(Collectors.groupingBy(Wish::getCategoryId, Collectors.counting()));

        List<Long> topCategoryIds = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_CATEGORY_LIMIT)
                .map(Map.Entry::getKey)
                .toList();
        if (topCategoryIds.isEmpty()) {
            return List.of();
        }

        Map<Long, String> nameById = categoryMapper.selectBatchIds(topCategoryIds).stream()
                .collect(Collectors.toMap(WishCategory::getId, WishCategory::getName));
        // 保持计数降序：按 topCategoryIds 顺序映射
        Map<Long, String> orderedNames = new LinkedHashMap<>();
        topCategoryIds.forEach(id -> orderedNames.put(id, nameById.getOrDefault(id, "未分类")));
        return orderedNames.entrySet().stream()
                .map(entry -> new AnnualReportVO.TopCategory(
                        entry.getValue(), categoryCounts.get(entry.getKey()).intValue()))
                .toList();
    }

    /**
     * 模板降级文案（AI 未生成/失败/未同意协议时使用，报告必达）。
     */
    private String buildTemplateSummary(int year, int fulfilledCount, int checkinDays) {
        if (fulfilledCount == 0 && checkinDays == 0) {
            return year + " 年你的心愿宇宙刚刚启程，还没有留下足迹。新的一年，从许下第一个心愿开始吧。";
        }
        StringBuilder summary = new StringBuilder(120);
        summary.append(year).append(" 年，你实现心愿 ").append(fulfilledCount).append(" 个")
                .append("，坚持打卡 ").append(checkinDays).append(" 天");
        if (fulfilledCount > 0) {
            summary.append("，每一份坚持都化作星光，照亮了你的心愿宇宙");
        } else {
            summary.append("，虽然心愿还在路上，但每一天的坚持都在积蓄能量");
        }
        return summary.append("。新的一年，愿你继续心怀热爱，奔赴山海。").toString();
    }

    private String milestoneTitle(GrowthRecordType type) {
        return switch (type) {
            case TEXT -> "记下了一段成长";
            case IMAGE -> "留下了图文印记";
            case VIDEO -> "记录了视频时刻";
            case DIARY -> "写下一篇心情日记";
        };
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= DESCRIPTION_MAX_LENGTH
                ? content : content.substring(0, DESCRIPTION_MAX_LENGTH) + "…";
    }
}
