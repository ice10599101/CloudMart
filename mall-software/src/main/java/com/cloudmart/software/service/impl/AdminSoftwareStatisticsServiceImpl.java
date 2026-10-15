package com.cloudmart.software.service.impl;

import com.cloudmart.software.repository.AdminSoftwareStatisticsMapper;
import com.cloudmart.software.service.AdminSoftwareStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运营统计实现（方案 10.7，P1）：窗口口径 30 天（获取/收藏/评分趋势），
 * 在架/待审/待办为当前快照；数字保留原始精度，均分一位小数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSoftwareStatisticsServiceImpl implements AdminSoftwareStatisticsService {

    private static final int WINDOW_DAYS = 30;
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");

    private final AdminSoftwareStatisticsMapper statisticsMapper;
    private final Clock clock;

    @Override
    public Map<String, Object> overview() {
        LocalDateTime from = LocalDateTime.now(clock).minusDays(WINDOW_DAYS);
        LocalDate fromDate = LocalDate.now(clock.withZone(PLATFORM_ZONE))
                .minusDays(WINDOW_DAYS - 1L);
        Double avgReviewMinutes = statisticsMapper.avgReviewMinutes(from);
        Double newRatingAvg = statisticsMapper.newRatingAvg(from);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("windowDays", WINDOW_DAYS);
        view.put("windowNote", "趋势类口径为最近 " + WINDOW_DAYS + " 天；"
                + "获取链接为站内去重获取人数/次数，不是外部下载量");
        view.put("publishedCount", statisticsMapper.publishedCount());
        view.put("pendingReviewCount", statisticsMapper.pendingReviewCount());
        view.put("avgReviewMinutes", avgReviewMinutes == null ? null
                : Math.round(avgReviewMinutes * 10) / 10.0);
        view.put("accessCount", statisticsMapper.accessCount(fromDate));
        view.put("accessUsers", statisticsMapper.accessUsers(fromDate));
        view.put("favoriteTotal", statisticsMapper.favoriteTotal());
        view.put("newFavorites", statisticsMapper.newFavorites(from));
        view.put("newRatings", statisticsMapper.newRatings(from));
        view.put("newRatingAvg", newRatingAvg == null ? null
                : Math.round(newRatingAvg * 10) / 10.0);
        view.put("pendingFeedbacks", statisticsMapper.pendingFeedbacks());
        view.put("pendingReports", statisticsMapper.pendingReports());
        return view;
    }
}
