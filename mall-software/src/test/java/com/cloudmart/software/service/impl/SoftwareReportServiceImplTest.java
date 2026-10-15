package com.cloudmart.software.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareReportCreateRequest;
import com.cloudmart.software.entity.SoftwareComment;
import com.cloudmart.software.entity.SoftwareReport;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.feign.SoftwareFileFeignClient;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareReportMapper;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwareRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户侧举报单测（A21 举报闭环数据来源）：可见性校验、目标属于软件、幂等落库、
 * 限频 429 语义（SOFTWARE_RATE_LIMITED）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareReportServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long SOFTWARE_ID = 100L;
    private static final Long REVISION_ID = 501L;
    private static final Long COMMENT_ID = 900L;

    @Mock
    private SoftwareAccessPolicy accessPolicy;
    @Mock
    private SoftwareCommentMapper commentMapper;
    @Mock
    private SoftwareReportMapper reportMapper;
    @Mock
    private SoftwareOperationService operationService;
    @Mock
    private SoftwareRateLimiter rateLimiter;
    @Mock
    private SoftwareFileFeignClient fileFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SoftwareReportServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(6)).get())
                .when(operationService).execute(anyString(), any(), anyString(), anyString(),
                        any(), any(), any(Supplier.class), any());
        // insert 回填自增 ID（MyBatis-Plus 语义）
        doAnswer(inv -> {
            ((SoftwareReport) inv.getArgument(0)).setId(1234L);
            return 1;
        }).when(reportMapper).insert(any(SoftwareReport.class));
        lenient().when(rateLimiter.allowReport(any())).thenReturn(true);

        SoftwareRevision published = new SoftwareRevision();
        published.setId(REVISION_ID);
        published.setSoftwareId(SOFTWARE_ID);
        published.setReviewStatus(SoftwareReviewStatus.APPROVED);
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(published);

        service = new SoftwareReportServiceImpl(accessPolicy, commentMapper, reportMapper,
                operationService, rateLimiter, fileFeignClient, new ObjectMapper(),
                transactionTemplate,
                Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.systemDefault()));
    }

    private SoftwareReportCreateRequest request(String targetType, Long targetId, String reason) {
        SoftwareReportCreateRequest request = new SoftwareReportCreateRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setReason(reason);
        return request;
    }

    @Test
    @DisplayName("举报软件：落库 OPEN，targetId 置为软件本体")
    void createSoftwareReport() {
        var result = service.create(USER_ID, SOFTWARE_ID, "rpt-key-1",
                request("SOFTWARE", null, "侵权抄袭"));

        assertThat(result.reportId()).isEqualTo(1234L);
        assertThat(result.status()).isEqualTo("OPEN");
        ArgumentCaptor<SoftwareReport> captor = ArgumentCaptor.forClass(SoftwareReport.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo("SOFTWARE");
        assertThat(captor.getValue().getTargetId()).isEqualTo(SOFTWARE_ID);
        assertThat(captor.getValue().getReporterId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("举报评论：仅该软件下的可见评论可举报")
    void createCommentReport_requiresVisibleCommentOfSameSoftware() {
        SoftwareComment comment = new SoftwareComment();
        comment.setId(COMMENT_ID);
        comment.setSoftwareId(SOFTWARE_ID);
        comment.setStatus(SoftwareCommentStatus.VISIBLE);
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(comment);

        var result = service.create(USER_ID, SOFTWARE_ID, "rpt-key-2",
                request("SOFTWARE_COMMENT", COMMENT_ID, "违规评论"));
        assertThat(result.status()).isEqualTo("OPEN");

        // 隐藏评论不可举报（不泄露目标状态，统一不存在提示）
        comment.setStatus(SoftwareCommentStatus.HIDDEN);
        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "rpt-key-3",
                request("SOFTWARE_COMMENT", COMMENT_ID, "违规评论")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");

        // 缺失 targetId
        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "rpt-key-4",
                request("SOFTWARE_COMMENT", null, "违规评论")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("targetId");
    }

    @Test
    @DisplayName("非法 targetType / 空理由 / 证据超限被拒")
    void createRejectsInvalidInput() {
        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "rpt-key-5",
                request("POST", null, "侵权")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("targetType");

        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "rpt-key-6",
                request("SOFTWARE", null, "  ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("举报理由");

        SoftwareReportCreateRequest evidence = request("SOFTWARE", null, "侵权");
        evidence.setEvidenceAssetIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "rpt-key-7", evidence))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("证据");
        verify(reportMapper, never()).insert(any(SoftwareReport.class));
    }

    @Test
    @DisplayName("限频命中：返回 SOFTWARE_RATE_LIMITED，不落库")
    void createRateLimited() {
        when(rateLimiter.allowReport(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(USER_ID, SOFTWARE_ID, "rpt-key-8",
                request("SOFTWARE", null, "侵权")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("频繁");
        verify(reportMapper, never()).insert(any(SoftwareReport.class));
    }

    @Test
    @DisplayName("证据素材：绑定失败不回滚举报（best-effort）")
    void evidenceBindingIsBestEffort() {
        when(fileFeignClient.bindReference(any(), any()))
                .thenThrow(new BusinessException("FILE_SERVICE_UNAVAILABLE", "文件服务不可用"));
        SoftwareReportCreateRequest request = request("SOFTWARE", null, "侵权");
        request.setEvidenceAssetIds(List.of(77L));

        var result = service.create(USER_ID, SOFTWARE_ID, "rpt-key-9", request);

        assertThat(result.reportId()).isEqualTo(1234L);
        verify(reportMapper).insert(any(SoftwareReport.class));
    }

    @Test
    @DisplayName("证据素材：绑定成功路径（引用键 SOFTWARE_REPORT）")
    void evidenceBindingSuccess() {
        when(fileFeignClient.bindReference(any(), any())).thenReturn(ApiResponse.ok(null));
        SoftwareReportCreateRequest request = request("SOFTWARE", null, "侵权");
        request.setEvidenceAssetIds(List.of(77L));

        service.create(USER_ID, SOFTWARE_ID, "rpt-key-10", request);

        verify(fileFeignClient).bindReference(eq(77L), any());
    }
}
