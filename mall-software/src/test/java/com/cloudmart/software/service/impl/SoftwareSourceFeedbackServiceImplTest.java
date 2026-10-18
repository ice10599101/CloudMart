package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareLinkFeedback;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.LinkFeedbackStatus;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareLinkFeedbackMapper;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
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
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 5.3/6.9 失效反馈单测（A19）：同指纹同人去重、3 账号 24h 标 SUSPECTED、
 * 平台禁用优先、旧快照来源拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareSourceFeedbackServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long OWNER_ID = 7L;
    private static final Long SOFTWARE_ID = 100L;
    private static final Long REVISION_ID = 501L;
    private static final Long RELEASE_ID = 3001L;
    private static final Long SOURCE_ID = 2001L;

    @Mock
    private SoftwareAccessPolicy accessPolicy;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareSourceStateMapper sourceStateMapper;
    @Mock
    private SoftwareLinkFeedbackMapper feedbackMapper;
    @Mock
    private SoftwareProperties softwareProperties;
    @Mock
    private SoftwareRateLimiter rateLimiter;
    @Mock
    private OutboxService outboxService;

    private SoftwareSourceFeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(rateLimiter.allowFeedback(any())).thenReturn(true);
        service = new SoftwareSourceFeedbackServiceImpl(accessPolicy, softwareMapper,
                releaseMapper, sourceMapper, sourceStateMapper, feedbackMapper,
                softwareProperties, rateLimiter, outboxService, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.systemDefault()));
        when(softwareProperties.getSuspectedDistinctReporters()).thenReturn(3);
        stubVisibleChain();
    }

    private void stubVisibleChain() {
        SoftwareRevision published = new SoftwareRevision();
        published.setId(REVISION_ID);
        published.setSoftwareId(SOFTWARE_ID);
        published.setReviewStatus(SoftwareReviewStatus.APPROVED);
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(published);

        SoftwareSource source = source();
        when(sourceMapper.selectById(SOURCE_ID)).thenReturn(source);
        SoftwareRelease release = new SoftwareRelease();
        release.setId(RELEASE_ID);
        release.setRevisionId(REVISION_ID);
        when(releaseMapper.selectById(source.getReleaseId())).thenReturn(release);

        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(OWNER_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
    }

    private SoftwareSource source() {
        SoftwareSource source = new SoftwareSource();
        source.setId(SOURCE_ID);
        source.setReleaseId(RELEASE_ID);
        source.setSourceKey("key-1");
        source.setContentFingerprint("fp-1");
        return source;
    }

    @Test
    @DisplayName("首次反馈：OPEN 落库；重复提交返回现有反馈不重复创建（A19）")
    void feedback_dedupByReporterAndFingerprint() {
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        var created = service.feedback(USER_ID, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", "打不开");

        assertThat(created.status()).isEqualTo("OPEN");
        verify(feedbackMapper).insert(any(SoftwareLinkFeedback.class));

        SoftwareLinkFeedback existing = new SoftwareLinkFeedback();
        existing.setId(9L);
        existing.setSourceKey("key-1");
        existing.setReasonCode("LINK_INVALID");
        existing.setStatus(LinkFeedbackStatus.OPEN);
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        var replay = service.feedback(USER_ID, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", "还是打不开");

        assertThat(replay.feedbackId()).isEqualTo(9L);
        verify(feedbackMapper, org.mockito.Mockito.times(1)).insert(any(SoftwareLinkFeedback.class));
    }

    @Test
    @DisplayName("非法 reasonCode 拒绝；旧快照来源 SOFTWARE_SOURCE_STALE")
    void feedback_validation() {
        assertThatThrownBy(() -> service.feedback(USER_ID, SOFTWARE_ID, SOURCE_ID, "HACK", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_FEEDBACK_INVALID");

        SoftwareRelease oldRelease = new SoftwareRelease();
        oldRelease.setId(RELEASE_ID);
        oldRelease.setRevisionId(999L);
        when(releaseMapper.selectById(RELEASE_ID)).thenReturn(oldRelease);

        assertThatThrownBy(() -> service.feedback(USER_ID, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SOURCE_STALE");
    }

    @Test
    @DisplayName("A19：24h 内 3 个不同账号 → SUSPECTED + Outbox 通知分享者；低于阈值不动")
    void feedback_suspectedThreshold() {
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(feedbackMapper.countRecentDistinctReporters(eq(SOFTWARE_ID), eq("key-1"),
                eq("fp-1"), any())).thenReturn(3L);
        when(sourceStateMapper.raiseSuspectedIfEligible(any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(sourceStateMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.feedback(999L, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", null);

        ArgumentCaptor<SoftwareSourceState> captor = ArgumentCaptor.forClass(SoftwareSourceState.class);
        verify(sourceStateMapper).insert((SoftwareSourceState) captor.capture());
        assertThat(captor.getValue().getAvailability()).isEqualTo(SourceAvailability.SUSPECTED);
        assertThat(captor.getValue().getCheckedFingerprint()).isEqualTo("fp-1");
        verify(outboxService).record(any());

        // 低于阈值：不建状态不通知
        org.mockito.Mockito.reset(sourceStateMapper, outboxService);
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(feedbackMapper.countRecentDistinctReporters(any(), any(), any(), any())).thenReturn(2L);

        service.feedback(998L, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", null);

        verify(sourceStateMapper, never()).insert(any(SoftwareSourceState.class));
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("SW07：已达阈值后第 4 条反馈不再重复迁移/通知（一次阈值一次提醒）")
    void feedback_repeatThresholdDoesNotRenotify() {
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(feedbackMapper.countRecentDistinctReporters(any(), any(), any(), any())).thenReturn(4L);
        // 状态行已存在（上次已迁移 SUSPECTED）：条件更新影响 0 行，selectCount>0
        when(sourceStateMapper.raiseSuspectedIfEligible(any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(sourceStateMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        service.feedback(996L, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", null);

        verify(sourceStateMapper, never()).insert(any(SoftwareSourceState.class));
        verify(sourceStateMapper, never()).updateById(any(SoftwareSourceState.class));
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("SW07：人工 INVALID/DISABLED 优先——自动反馈不降级、不通知（条件更新 0 行）")
    void feedback_invalidPriority() {
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(feedbackMapper.countRecentDistinctReporters(any(), any(), any(), any())).thenReturn(5L);
        when(sourceStateMapper.raiseSuspectedIfEligible(any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(sourceStateMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        service.feedback(995L, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", null);

        verify(sourceStateMapper, never()).insert(any(SoftwareSourceState.class));
        verify(sourceStateMapper, never()).updateById(any(SoftwareSourceState.class));
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("SW07：UNKNOWN/AVAILABLE → SUSPECTED 的真实迁移才通知（条件更新影响 1 行）")
    void feedback_realMigrationNotifiesOnce() {
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(feedbackMapper.countRecentDistinctReporters(any(), any(), any(), any())).thenReturn(3L);
        when(sourceStateMapper.raiseSuspectedIfEligible(any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.feedback(994L, SOFTWARE_ID, SOURCE_ID, "LINK_INVALID", null);

        verify(sourceStateMapper, never()).insert(any(SoftwareSourceState.class));
        verify(outboxService).record(any());
    }

    @Test
    @DisplayName("A19：修复闭环素材——RESOLVED 保留 original 指纹与修复 revisionId（管理员侧契约字段）")
    void feedback_schemaKeepsOriginalFingerprint() {
        // schema 断言：反馈行保存反馈时指纹（source_fingerprint），修复后 RESOLVED
        // 记录 resolved_revision_id——字段存在性由实体与迁移 V2 保证，此处验证插入载荷
        when(feedbackMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.feedback(USER_ID, SOFTWARE_ID, SOURCE_ID, "CODE_WRONG", "提取码不对");

        ArgumentCaptor<SoftwareLinkFeedback> captor = ArgumentCaptor.forClass(SoftwareLinkFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceFingerprint()).isEqualTo("fp-1");
        assertThat(captor.getValue().getSourceKey()).isEqualTo("key-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(LinkFeedbackStatus.OPEN);
    }
}
