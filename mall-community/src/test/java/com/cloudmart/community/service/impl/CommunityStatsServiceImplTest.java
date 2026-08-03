package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.entity.Report;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.repository.ReportMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityStatsServiceImplTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostCommentMapper postCommentMapper;

    @Mock
    private ReportMapper reportMapper;

    private CommunityStatsServiceImpl communityStatsService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant1 = new MapperBuilderAssistant(configuration, "");
        assistant1.setCurrentNamespace("com.cloudmart.community.repository.PostMapper");
        TableInfoHelper.initTableInfo(assistant1, Post.class);
        MapperBuilderAssistant assistant2 = new MapperBuilderAssistant(configuration, "");
        assistant2.setCurrentNamespace("com.cloudmart.community.repository.PostCommentMapper");
        TableInfoHelper.initTableInfo(assistant2, PostComment.class);
        MapperBuilderAssistant assistant3 = new MapperBuilderAssistant(configuration, "");
        assistant3.setCurrentNamespace("com.cloudmart.community.repository.ReportMapper");
        TableInfoHelper.initTableInfo(assistant3, Report.class);
    }

    @BeforeEach
    void setUp() {
        communityStatsService = new CommunityStatsServiceImpl(
                postMapper, postCommentMapper, reportMapper
        );
    }

    @Nested
    @DisplayName("getOverviewStats")
    class GetOverviewStatsTests {

        @Test
        @DisplayName("should return all overview stat fields")
        void getOverviewStats_success() {
            when(postMapper.selectCount(any())).thenReturn(100L, 10L, 5L, 3L);
            when(postCommentMapper.selectCount(any())).thenReturn(200L, 20L);
            when(reportMapper.selectCount(any())).thenReturn(8L, 15L);

            Map<String, Object> stats = communityStatsService.getOverviewStats();

            assertThat(stats).containsEntry("totalPostCount", 100L);
            assertThat(stats).containsEntry("todayPostCount", 10L);
            assertThat(stats).containsEntry("pendingReviewCount", 5L);
            assertThat(stats).containsEntry("rejectedPostCount", 3L);
            assertThat(stats).containsEntry("totalCommentCount", 200L);
            assertThat(stats).containsEntry("todayCommentCount", 20L);
            assertThat(stats).containsEntry("pendingReportCount", 8L);
            assertThat(stats).containsEntry("totalReportCount", 15L);
        }

        @Test
        @DisplayName("should return zero counts when no data")
        void getOverviewStats_zeroCounts() {
            when(postMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L);
            when(postCommentMapper.selectCount(any())).thenReturn(0L, 0L);
            when(reportMapper.selectCount(any())).thenReturn(0L, 0L);

            Map<String, Object> stats = communityStatsService.getOverviewStats();

            assertThat(stats).containsEntry("totalPostCount", 0L);
            assertThat(stats).containsEntry("totalCommentCount", 0L);
            assertThat(stats).containsEntry("totalReportCount", 0L);
        }

        @Test
        @DisplayName("should have exactly 8 stat keys")
        void getOverviewStats_correctKeyCount() {
            when(postMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L);
            when(postCommentMapper.selectCount(any())).thenReturn(0L, 0L);
            when(reportMapper.selectCount(any())).thenReturn(0L, 0L);

            Map<String, Object> stats = communityStatsService.getOverviewStats();

            assertThat(stats).hasSize(8);
        }
    }

    @Nested
    @DisplayName("getTrendStats")
    class GetTrendStatsTests {

        @Test
        @DisplayName("should return trend data for specified days")
        void getTrendStats_success() {
            when(postMapper.selectCount(any())).thenReturn(5L);
            when(postCommentMapper.selectCount(any())).thenReturn(10L);
            when(reportMapper.selectCount(any())).thenReturn(2L);

            List<Map<String, Object>> trend = communityStatsService.getTrendStats(3);

            assertThat(trend).hasSize(3);
            assertThat(trend.get(0)).containsKey("date");
            assertThat(trend.get(0)).containsEntry("postCount", 5L);
            assertThat(trend.get(0)).containsEntry("commentCount", 10L);
            assertThat(trend.get(0)).containsEntry("reportCount", 2L);
        }

        @Test
        @DisplayName("should clamp days to max 90")
        void getTrendStats_clampMaxDays() {
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(postCommentMapper.selectCount(any())).thenReturn(0L);
            when(reportMapper.selectCount(any())).thenReturn(0L);

            List<Map<String, Object>> trend = communityStatsService.getTrendStats(100);

            assertThat(trend).hasSize(90);
        }

        @Test
        @DisplayName("should clamp days to min 1")
        void getTrendStats_clampMinDays() {
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(postCommentMapper.selectCount(any())).thenReturn(0L);
            when(reportMapper.selectCount(any())).thenReturn(0L);

            List<Map<String, Object>> trend = communityStatsService.getTrendStats(0);

            assertThat(trend).hasSize(1);
        }

        @Test
        @DisplayName("should return single day trend data")
        void getTrendStats_singleDay() {
            when(postMapper.selectCount(any())).thenReturn(7L);
            when(postCommentMapper.selectCount(any())).thenReturn(15L);
            when(reportMapper.selectCount(any())).thenReturn(1L);

            List<Map<String, Object>> trend = communityStatsService.getTrendStats(1);

            assertThat(trend).hasSize(1);
            assertThat(trend.get(0)).containsEntry("postCount", 7L);
        }
    }
}
