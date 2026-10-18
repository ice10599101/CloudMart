package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminFeedbackMarkRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareLinkFeedback;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.LinkFeedbackStatus;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareLinkFeedbackMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareReportMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.common.async.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SW07：修复结案事实校验——RESOLVED 必须引用本软件当前已发布快照且具备修复事实；
 * 终态反馈不能静默改状态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSoftwareModerationServiceImplTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long SOFTWARE_ID = 101L;
    private static final Long PUBLISHED_REVISION = 203L;
    private static final Long OTHER_REVISION = 999L;
    private static final Long RELEASE_ID = 307L;
    private static final Long FEEDBACK_ID = 409L;
    private static final String SOURCE_KEY = "key-1";
    private static final String OLD_FINGERPRINT = "fp-old";
    private static final String NEW_FINGERPRINT = "fp-new";

    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareLockMapper lockMapper;
    @Mock
    private SoftwareReportMapper reportMapper;
    @Mock
    private SoftwareCommentMapper commentMapper;
    @Mock
    private SoftwareLinkFeedbackMapper feedbackMapper;
    @Mock
    private SoftwareAuditLogMapper auditLogMapper;
    @Mock
    private UserSupportFeignClient userSupportFeignClient;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareSourceStateMapper sourceStateMapper;
    @Mock
    private SoftwareOperationService operationService;
    @Mock
    private com.cloudmart.software.service.SoftwarePlatformActionService platformActionService;
    @Mock
    private com.cloudmart.software.service.SoftwareCommentStatusService commentStatusService;
    @Mock
    private OutboxService outboxService;

    private AdminSoftwareModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSoftwareModerationServiceImpl(softwareMapper, lockMapper, reportMapper,
                commentMapper, feedbackMapper, auditLogMapper, userSupportFeignClient,
                revisionMapper, releaseMapper, sourceMapper, sourceStateMapper,
                operationService, platformActionService, commentStatusService,
                outboxService, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.systemDefault()));
        // operationService.execute 直通业务动作（幂等语义属 SW05 职责，这里只测 doMark）
        lenient().when(operationService.execute(anyString(), eq(ADMIN_ID), anyString(),
                anyString(), any(), any(), any(Supplier.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<Void> action = invocation.getArgument(6);
                    action.get();
                    return null;
                });
        stubSoftware(PUBLISHED_REVISION);
        stubPublishedRevision(PUBLISHED_REVISION);
    }

    private void stubSoftware(Long publishedRevisionId) {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(7L);
        software.setPublishedRevisionId(publishedRevisionId);
        lenient().when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
    }

    private void stubPublishedRevision(Long revisionId) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(revisionId);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setReviewStatus(SoftwareReviewStatus.APPROVED);
        lenient().when(revisionMapper.selectById(revisionId)).thenReturn(revision);
        SoftwareRelease release = new SoftwareRelease();
        release.setId(RELEASE_ID);
        release.setRevisionId(revisionId);
        lenient().when(releaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(release));
    }

    private SoftwareLinkFeedback feedback(LinkFeedbackStatus status) {
        SoftwareLinkFeedback feedback = new SoftwareLinkFeedback();
        feedback.setId(FEEDBACK_ID);
        feedback.setSoftwareId(SOFTWARE_ID);
        feedback.setSourceKey(SOURCE_KEY);
        feedback.setSourceFingerprint(OLD_FINGERPRINT);
        feedback.setStatus(status);
        feedback.setLockVersion(0);
        feedback.setCreatedAt(LocalDateTime.parse("2026-10-14T10:00:00"));
        return feedback;
    }

    private SoftwareSource sourceWith(String fingerprint) {
        SoftwareSource source = new SoftwareSource();
        source.setId(1L);
        source.setReleaseId(RELEASE_ID);
        source.setSourceKey(SOURCE_KEY);
        source.setContentFingerprint(fingerprint);
        return source;
    }

    private void stubPublishedSources(SoftwareSource... sources) {
        lenient().when(sourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sources));
    }

    private AdminFeedbackMarkRequest mark(String status, Long resolvedRevisionId) {
        AdminFeedbackMarkRequest request = new AdminFeedbackMarkRequest();
        request.setStatus(status);
        request.setResolvedRevisionId(resolvedRevisionId);
        return request;
    }

    @Test
    @DisplayName("结案必须引用本软件当前已发布快照：他软件/未上线/任意 revisionId 拒绝")
    void markResolved_requiresPublishedRevisionOfSameSoftware() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(LinkFeedbackStatus.VERIFYING));
        stubPublishedSources(sourceWith(NEW_FINGERPRINT));
        when(sourceStateMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        // 引用非当前发布快照
        assertThatThrownBy(() -> service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k1",
                mark("RESOLVED", OTHER_REVISION)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_FEEDBACK_INVALID");
        // 软件没有发布快照
        stubSoftware(null);
        assertThatThrownBy(() -> service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k2",
                mark("RESOLVED", OTHER_REVISION)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_FEEDBACK_INVALID");
    }

    @Test
    @DisplayName("来源仍在但缺少核验事实（无 AVAILABLE/指纹不符/核验早于反馈）拒绝结案")
    void markResolved_missingFactRejected() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(LinkFeedbackStatus.VERIFYING));
        stubPublishedSources(sourceWith(NEW_FINGERPRINT));

        // 无状态行
        when(sourceStateMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k1",
                mark("RESOLVED", PUBLISHED_REVISION)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_FEEDBACK_INVALID");

        // 有 AVAILABLE 但指纹是旧的（旧结论不适用于新内容）
        SoftwareSourceState stale = new SoftwareSourceState();
        stale.setAvailability(SourceAvailability.AVAILABLE);
        stale.setCheckedFingerprint(OLD_FINGERPRINT);
        stale.setCheckedAt(LocalDateTime.parse("2026-10-15T11:00:00"));
        when(sourceStateMapper.selectOne(any(Wrapper.class))).thenReturn(stale);
        assertThatThrownBy(() -> service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k2",
                mark("RESOLVED", PUBLISHED_REVISION)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_FEEDBACK_INVALID");

        // 核验时间早于反馈
        stale.setCheckedFingerprint(NEW_FINGERPRINT);
        stale.setCheckedAt(LocalDateTime.parse("2026-10-13T11:00:00"));
        assertThatThrownBy(() -> service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k3",
                mark("RESOLVED", PUBLISHED_REVISION)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_FEEDBACK_INVALID");
    }

    @Test
    @DisplayName("换链接后管理员核验 AVAILABLE（指纹=新内容、核验晚于反馈）→ 结案成功并保留原指纹")
    void markResolved_verifiedNewFingerprintSucceeds() {
        SoftwareLinkFeedback fb = feedback(LinkFeedbackStatus.VERIFYING);
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(fb);
        stubPublishedSources(sourceWith(NEW_FINGERPRINT));
        SoftwareSourceState verified = new SoftwareSourceState();
        verified.setAvailability(SourceAvailability.AVAILABLE);
        verified.setCheckedFingerprint(NEW_FINGERPRINT);
        verified.setCheckedAt(LocalDateTime.parse("2026-10-15T11:00:00"));
        when(sourceStateMapper.selectOne(any(Wrapper.class))).thenReturn(verified);

        service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k1", mark("RESOLVED", PUBLISHED_REVISION));

        ArgumentCaptor<SoftwareLinkFeedback> captor = ArgumentCaptor.forClass(SoftwareLinkFeedback.class);
        verify(feedbackMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LinkFeedbackStatus.RESOLVED);
        assertThat(captor.getValue().getResolvedRevisionId()).isEqualTo(PUBLISHED_REVISION);
        // 原始指纹永久保留
        assertThat(captor.getValue().getSourceFingerprint()).isEqualTo(OLD_FINGERPRINT);
    }

    @Test
    @DisplayName("无效来源已随修复快照上线移除 → 无需核验即可结案")
    void markResolved_sourceRemovedSucceeds() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(LinkFeedbackStatus.VERIFYING));
        // 发布快照已不含该 sourceKey
        SoftwareSource other = sourceWith(NEW_FINGERPRINT);
        other.setSourceKey("key-other");
        stubPublishedSources(other);

        service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k1", mark("RESOLVED", PUBLISHED_REVISION));

        verify(feedbackMapper).updateById(any(SoftwareLinkFeedback.class));
    }

    @Test
    @DisplayName("终态反馈：重复同意图幂等返回不写库；不同意图拒绝并提示显式重开")
    void markTerminal_idempotentSameIntentConflictingIntentRejected() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(LinkFeedbackStatus.RESOLVED));

        // 同意图：RESOLVED → RESOLVED，幂等返回
        service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k1", mark("RESOLVED", PUBLISHED_REVISION));
        verify(feedbackMapper, never()).updateById(any(SoftwareLinkFeedback.class));

        // 不同意图：RESOLVED → REJECTED 拒绝
        assertThatThrownBy(() -> service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k2",
                mark("REJECTED", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_FEEDBACK_TERMINAL");

        // 显式重开：RESOLVED → VERIFYING 允许
        service.markFeedback(ADMIN_ID, FEEDBACK_ID, "k3", mark("VERIFYING", null));
        verify(feedbackMapper).updateById(any(SoftwareLinkFeedback.class));
    }
}
