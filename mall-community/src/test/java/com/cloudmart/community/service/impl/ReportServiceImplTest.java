package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateReportRequest;
import com.cloudmart.community.dto.HandleReportRequest;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.entity.Report;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.repository.ReportMapper;
import com.cloudmart.community.vo.ReportVO;
import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostCommentMapper postCommentMapper;

    @Mock
    private ObjectMapper objectMapper;

    private ReportServiceImpl reportService;

    private static final Long REPORTER_ID = 1L;
    private static final Long HANDLER_ID = 10L;
    private static final Long REPORT_ID = 100L;
    private static final Long TARGET_ID = 200L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant reportAssistant = new MapperBuilderAssistant(configuration, "");
        reportAssistant.setCurrentNamespace("com.cloudmart.community.repository.ReportMapper");
        TableInfoHelper.initTableInfo(reportAssistant, Report.class);
        MapperBuilderAssistant postAssistant = new MapperBuilderAssistant(configuration, "");
        postAssistant.setCurrentNamespace("com.cloudmart.community.repository.PostMapper");
        TableInfoHelper.initTableInfo(postAssistant, Post.class);
        MapperBuilderAssistant commentAssistant = new MapperBuilderAssistant(configuration, "");
        commentAssistant.setCurrentNamespace("com.cloudmart.community.repository.PostCommentMapper");
        TableInfoHelper.initTableInfo(commentAssistant, PostComment.class);
    }

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                reportMapper, postMapper, postCommentMapper, objectMapper);
    }

    private Report buildReport() {
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporterId(REPORTER_ID);
        report.setTargetType("POST");
        report.setTargetId(TARGET_ID);
        report.setReason("SPAM");
        report.setDescription("Spam content");
        report.setStatus(0);
        return report;
    }

    private Post buildPost() {
        Post post = new Post();
        post.setId(TARGET_ID);
        post.setUserId(2L);
        post.setTitle("Spam Post");
        post.setContent("Bad content");
        post.setStatus(1);
        return post;
    }

    private PostComment buildComment() {
        PostComment comment = new PostComment();
        comment.setId(TARGET_ID);
        comment.setPostId(300L);
        comment.setUserId(2L);
        comment.setContent("Bad comment");
        comment.setStatus(0);
        return comment;
    }

    @Nested
    @DisplayName("createReport")
    class CreateReportTests {

        @Test
        @DisplayName("should create report with images")
        void createReport_withImages() throws Exception {
            CreateReportRequest request = new CreateReportRequest(
                    "POST", TARGET_ID, "SPAM", "Spam content", List.of("img1.jpg", "img2.jpg"));
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"img1.jpg\",\"img2.jpg\"]");

            reportService.createReport(REPORTER_ID, request);

            verify(reportMapper).insert(any(Report.class));
        }

        @Test
        @DisplayName("should create report without images")
        void createReport_noImages() {
            CreateReportRequest request = new CreateReportRequest(
                    "COMMENT", TARGET_ID, "ABUSE", "Abusive content", null);

            reportService.createReport(REPORTER_ID, request);

            verify(reportMapper).insert(any(Report.class));
        }

        @Test
        @DisplayName("should set initial status to 0")
        void createReport_initialStatus() {
            CreateReportRequest request = new CreateReportRequest(
                    "POST", TARGET_ID, "SPAM", "desc", null);
            when(reportMapper.insert(any(Report.class))).thenAnswer(invocation -> {
                Report report = invocation.getArgument(0);
                assertThat(report.getStatus()).isEqualTo(0);
                assertThat(report.getReporterId()).isEqualTo(REPORTER_ID);
                return 1;
            });

            reportService.createReport(REPORTER_ID, request);
        }
    }

    @Nested
    @DisplayName("adminListReports")
    class AdminListReportsTests {

        @Test
        @DisplayName("should return paginated reports")
        void adminListReports_success() {
            Report report = buildReport();
            Page<Report> reportPage = new Page<>(1, 10, 1);
            reportPage.setRecords(List.of(report));
            when(reportMapper.selectPage(any(Page.class), any())).thenReturn(reportPage);

            Page<ReportVO> result = reportService.adminListReports(null, null, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("should filter by status when provided")
        void adminListReports_filterByStatus() {
            Report report = buildReport();
            Page<Report> reportPage = new Page<>(1, 10, 1);
            reportPage.setRecords(List.of(report));
            when(reportMapper.selectPage(any(Page.class), any())).thenReturn(reportPage);

            Page<ReportVO> result = reportService.adminListReports(0, null, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            verify(reportMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("should filter by target type when provided")
        void adminListReports_filterByTargetType() {
            Page<Report> reportPage = new Page<>(1, 10, 0);
            reportPage.setRecords(List.of());
            when(reportMapper.selectPage(any(Page.class), any())).thenReturn(reportPage);

            Page<ReportVO> result = reportService.adminListReports(null, "POST", 1, 10);

            assertThat(result.getRecords()).isEmpty();
        }
    }

    @Nested
    @DisplayName("handleReport")
    class HandleReportTests {

        @Test
        @DisplayName("should handle report and update status")
        void handleReport_success() {
            Report report = buildReport();
            when(reportMapper.selectById(REPORT_ID)).thenReturn(report);

            HandleReportRequest request = new HandleReportRequest(2, "Resolved");
            reportService.handleReport(HANDLER_ID, REPORT_ID, request);

            assertThat(report.getStatus()).isEqualTo(2);
            assertThat(report.getHandlerId()).isEqualTo(HANDLER_ID);
            assertThat(report.getHandleNote()).isEqualTo("Resolved");
            assertThat(report.getHandledAt()).isNotNull();
            verify(reportMapper).updateById(report);
        }

        @Test
        @DisplayName("should throw when report not found")
        void handleReport_notFound_throwsException() {
            when(reportMapper.selectById(REPORT_ID)).thenReturn(null);

            HandleReportRequest request = new HandleReportRequest(2, "Resolved");

            assertThatThrownBy(() -> reportService.handleReport(HANDLER_ID, REPORT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("REPORT_NOT_FOUND");
                    });

            verify(reportMapper, never()).updateById(any(Report.class));
        }

        @Test
        @DisplayName("should hide post when status is 3 and target is POST")
        void handleReport_hidePost() {
            Report report = buildReport();
            when(reportMapper.selectById(REPORT_ID)).thenReturn(report);

            Post post = buildPost();
            when(postMapper.selectById(TARGET_ID)).thenReturn(post);

            HandleReportRequest request = new HandleReportRequest(3, "Violation confirmed");
            reportService.handleReport(HANDLER_ID, REPORT_ID, request);

            assertThat(post.getStatus()).isEqualTo(2);
            verify(postMapper).updateById(post);
        }

        @Test
        @DisplayName("should hide comment when status is 3 and target is COMMENT")
        void handleReport_hideComment() {
            Report report = buildReport();
            report.setTargetType("COMMENT");
            when(reportMapper.selectById(REPORT_ID)).thenReturn(report);

            PostComment comment = buildComment();
            when(postCommentMapper.selectById(TARGET_ID)).thenReturn(comment);

            HandleReportRequest request = new HandleReportRequest(3, "Violation confirmed");
            reportService.handleReport(HANDLER_ID, REPORT_ID, request);

            assertThat(comment.getStatus()).isEqualTo(1);
            verify(postCommentMapper).updateById(comment);
        }

        @Test
        @DisplayName("should not hide target when status is not 3")
        void handleReport_noHideWhenNotStatus3() {
            Report report = buildReport();
            when(reportMapper.selectById(REPORT_ID)).thenReturn(report);

            HandleReportRequest request = new HandleReportRequest(1, "Under review");
            reportService.handleReport(HANDLER_ID, REPORT_ID, request);

            verify(postMapper, never()).selectById(anyLong());
            verify(postCommentMapper, never()).selectById(anyLong());
        }
    }
}
