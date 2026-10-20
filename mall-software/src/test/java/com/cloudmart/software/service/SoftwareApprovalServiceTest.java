package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.vo.software.SoftwareReviewDecisionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SW04 审核结果落库：指针切换/序号/Outbox/fanout 与拒绝克隆必须保持一致，
 * 自动检查与人工审核共用同一实现。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareApprovalServiceTest {

    private static final Long SOFTWARE_ID = 100L;
    private static final Long REVISION_ID = 501L;
    private static final Long OLD_REVISION_ID = 400L;
    private static final Long OWNER_ID = 42L;
    private static final Long ADMIN_ID = 1000L;
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareAuditService auditService;
    @Mock
    private com.cloudmart.common.async.outbox.OutboxService outboxService;
    @Mock
    private SoftwareReleaseFanoutService fanoutService;
    @Mock
    private SoftwareSnapshotAssembler assembler;

    private SoftwareApprovalService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Software.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareApprovalService(softwareMapper, revisionMapper, auditService,
                outboxService, fanoutService, assembler, new ObjectMapper(),
                Clock.fixed(NOW, ZoneId.systemDefault()));
    }

    private Software software(Long publishedRevisionId, int publicationSeq) {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(OWNER_ID);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        software.setPublishedRevisionId(publishedRevisionId);
        software.setPublicationSeq(publicationSeq);
        return software;
    }

    private SoftwareRevision revision(SoftwareReviewStatus status) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setReviewStatus(status);
        return revision;
    }

    @Test
    @DisplayName("SW04：更新发布切换 published 指针、清空 working、seq+1，并广播订阅事件")
    void publishApprovedSwitchesPointersAndFansOut() {
        Software locked = software(OLD_REVISION_ID, 1);
        SoftwareRevision revision = revision(SoftwareReviewStatus.PENDING);

        SoftwareReviewDecisionVO decision = service.publishApproved(locked, revision,
                ADMIN_ID, "ADMIN", "APPROVE", "PENDING", "内容合格", "req-1");

        assertThat(decision.decision()).isEqualTo("APPROVED");
        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.APPROVED);
        assertThat(locked.getPublishedRevisionId()).isEqualTo(REVISION_ID);
        assertThat(locked.getWorkingRevisionId()).isNull();
        assertThat(locked.getPublicationSeq()).isEqualTo(2);
        assertThat(locked.getPublishedAt()).isNotNull();
        verify(revisionMapper).updateById(revision);
        verify(softwareMapper).update(isNull(), any());
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(ADMIN_ID),
                eq("ADMIN"), eq("APPROVE"), eq("PENDING"), eq("APPROVED"),
                eq("内容合格"), eq("req-1"));
        // REVIEW_APPROVED + RELEASE_PUBLISHED 两条；fanout 携带上一线上快照用于差集
        verify(outboxService, times(2)).record(any());
        verify(fanoutService).registerTask(SOFTWARE_ID, REVISION_ID, 2,
                locked.getLastReleaseAt(), OLD_REVISION_ID);
    }

    @Test
    @DisplayName("SW04：首次上线只写审核通过事件，不触发订阅广播")
    void firstPublicationDoesNotFanOut() {
        Software locked = software(null, 0);
        SoftwareRevision revision = revision(SoftwareReviewStatus.PENDING);

        service.publishApproved(locked, revision, ADMIN_ID, "ADMIN", "APPROVE",
                "PENDING", "ok", null);

        verify(outboxService, times(1)).record(any());
        verify(fanoutService, never()).registerTask(any(), any(), any(), any(), any());
        assertThat(locked.getPublicationSeq()).isEqualTo(1);
    }

    @Test
    @DisplayName("SW04：拒绝保留旧记录并克隆新 DRAFT，working 指针切到副本")
    void rejectClonesEditableDraft() {
        Software locked = software(OLD_REVISION_ID, 1);
        SoftwareRevision revision = revision(SoftwareReviewStatus.PENDING);
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT);
        draft.setId(600L);
        when(assembler.cloneInto(revision, SOFTWARE_ID, OWNER_ID)).thenReturn(draft);

        SoftwareRevision result = service.rejectAndClone(locked, revision, ADMIN_ID, "ADMIN",
                "REJECT", "PENDING", "内容不合规", "req-2");

        assertThat(result).isSameAs(draft);
        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.REJECTED);
        assertThat(draft.getCopiedFromRevisionId()).isEqualTo(REVISION_ID);
        assertThat(locked.getWorkingRevisionId()).isEqualTo(600L);
        assertThat(locked.getPublishedRevisionId()).isEqualTo(OLD_REVISION_ID);
        verify(revisionMapper).insert(draft);
        verify(assembler).cloneChildren(REVISION_ID, 600L);
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(ADMIN_ID),
                eq("ADMIN"), eq("REJECT"), eq("PENDING"), eq("REJECTED"),
                eq("内容不合规"), eq("req-2"));
        ArgumentCaptor<String> cloneNote = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(SOFTWARE_ID), eq(600L), eq(ADMIN_ID),
                eq("ADMIN"), eq("CLONE_REJECTED"), eq("REJECTED"), eq("DRAFT"),
                cloneNote.capture(), eq("req-2"));
        assertThat(cloneNote.getValue()).contains("parentRevisionId=" + REVISION_ID);
    }
}
