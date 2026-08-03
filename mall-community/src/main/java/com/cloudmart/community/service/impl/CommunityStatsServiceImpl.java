package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.entity.Report;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.repository.ReportMapper;
import com.cloudmart.community.service.CommunityStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityStatsServiceImpl implements CommunityStatsService {

    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;
    private final ReportMapper reportMapper;

    @Override
    public Map<String, Object> getOverviewStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Long totalPostCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1));

        Long todayPostCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, 1)
                        .ge(Post::getCreatedAt, todayStart));

        Long pendingReviewCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getReviewStatus, 0));

        Long totalCommentCount = postCommentMapper.selectCount(
                new LambdaQueryWrapper<PostComment>().eq(PostComment::getStatus, 0));

        Long todayCommentCount = postCommentMapper.selectCount(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getStatus, 0)
                        .ge(PostComment::getCreatedAt, todayStart));

        Long pendingReportCount = reportMapper.selectCount(
                new LambdaQueryWrapper<Report>().eq(Report::getStatus, 0));

        Long totalReportCount = reportMapper.selectCount(new LambdaQueryWrapper<>());

        Long rejectedPostCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getReviewStatus, 2));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPostCount", totalPostCount);
        stats.put("todayPostCount", todayPostCount);
        stats.put("pendingReviewCount", pendingReviewCount);
        stats.put("rejectedPostCount", rejectedPostCount);
        stats.put("totalCommentCount", totalCommentCount);
        stats.put("todayCommentCount", todayCommentCount);
        stats.put("pendingReportCount", pendingReportCount);
        stats.put("totalReportCount", totalReportCount);
        return stats;
    }

    @Override
    public List<Map<String, Object>> getTrendStats(int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        List<Map<String, Object>> trend = new ArrayList<>();

        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            Long postCount = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getStatus, 1)
                            .ge(Post::getCreatedAt, dayStart)
                            .lt(Post::getCreatedAt, dayEnd)
            );

            Long commentCount = postCommentMapper.selectCount(
                    new LambdaQueryWrapper<PostComment>()
                            .eq(PostComment::getStatus, 0)
                            .ge(PostComment::getCreatedAt, dayStart)
                            .lt(PostComment::getCreatedAt, dayEnd)
            );

            Long reportCount = reportMapper.selectCount(
                    new LambdaQueryWrapper<Report>()
                            .ge(Report::getCreatedAt, dayStart)
                            .lt(Report::getCreatedAt, dayEnd)
            );

            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", date.toString());
            dayData.put("postCount", postCount);
            dayData.put("commentCount", commentCount);
            dayData.put("reportCount", reportCount);
            trend.add(dayData);
        }

        return trend;
    }
}
