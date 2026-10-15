package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.policy.SoftwarePublisherPolicy;
import com.cloudmart.software.repository.SoftwareFavoriteMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.service.SoftwareAuditService;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwareSnapshotAssembler;
import com.cloudmart.software.vo.software.SoftwareCreatedVO;
import com.cloudmart.software.vo.software.SoftwareReviewDecisionVO;
import com.cloudmart.software.vo.software.SoftwareSubmittedVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1/4.2 状态机与幂等单测（S04 出口条件）：重试不重复创建（operation 拦截）、
 * 新旧快照切换正确（approve 同事务切指针/清 working/序号+1/Outbox）、
 * 旧批次 409（A07）、资格复核（A33）、BANNED 不可 relist（4.2）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwarePublishServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long ADMIN_ID = 1000L;
    private static final Long SOFTWARE_ID = 100L;
    private static final Long REVISION_ID = 501L;
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareRevisionTagMapper revisionTagMapper;
    @Mock
    private SoftwareRevisionMediaMapper revisionMediaMapper;
    @Mock
    private SoftwareFavoriteMapper favoriteMapper;
    @Mock
    private SoftwareLockMapper lockMapper;
    @Mock
    private SoftwarePublisherPolicy publisherPolicy;
    @Mock
    private SoftwareSnapshotAssembler assembler;
    @Mock
    private SoftwareOperationService operationService;
    @Mock
    private SoftwareAuditService auditService;
    @Mock
    private OutboxService outboxService;
    @Mock
    private com.cloudmart.software.feign.CommunitySupportFeignClient communitySupportFeignClient;
    @Mock
    private com.cloudmart.software.feign.UserSupportFeignClient userSupportFeignClient;
    @Mock
    private SoftwareProperties softwareProperties;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SoftwarePublishServiceImpl service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate 直接执行回调（mock 事务管理器语义）
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        service = new SoftwarePublishServiceImpl(softwareMapper, revisionMapper, releaseMapper,
                sourceMapper, revisionTagMapper, revisionMediaMapper, favoriteMapper, lockMapper,
                publisherPolicy, assembler, operationService, auditService, outboxService,
                communitySupportFeignClient, userSupportFeignClient, softwareProperties,
                new ObjectMapper(), Clock.fixed(NOW, ZoneId.systemDefault()), transactionTemplate);
        // operationService 透传业务动作（真实执行状态机逻辑）
        lenient().when(operationService.execute(anyString(), any(), anyString(), anyString(),
                any(), any(), any(Supplier.class), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(6)).get());
        lenient().when(softwareProperties.isPublishEnabled()).thenReturn(true);
        doNothing().when(publisherPolicy).requireForRestrictedWrite(any());
        doNothing().when(assembler).validateForSubmit(any());
    }

    private Software software(SoftwareLifecycle lifecycle, Long publishedId, Long workingId) {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(USER_ID);
        software.setLifecycle(lifecycle);
        software.setPublishedRevisionId(publishedId);
        software.setWorkingRevisionId(workingId);
        software.setPublicationSeq(1);
        software.setLockVersion(3);
        software.setCommentCount(0);
        software.setFavoriteCount(0);
        software.setRatingCount(0);
        software.setRatingSum(0);
        software.setLinkAccessCount(0L);
        return software;
    }

    private SoftwareRevision revision(SoftwareReviewStatus status, int submitSeq, int lockVersion) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setEditorId(USER_ID);
        revision.setRevisionNo(1);
        revision.setReviewStatus(status);
        revision.setSubmitSeq(submitSeq);
        revision.setLockVersion(lockVersion);
        revision.setAccessMode(com.cloudmart.software.enums.SoftwareAccessMode.REGISTERED);
        revision.setName("示例工具");
        revision.setSummary("用于整理工作资料的工具");
        return revision;
    }

    private SoftwarePublisherPermission permission(boolean canPublish) {
        SoftwarePublisherPermission permission = new SoftwarePublisherPermission();
        permission.setUserId(USER_ID);
        permission.setCanPublish(canPublish);
        return permission;
    }

    @Test
    @DisplayName("创建软件：资格矩阵前置 + ACTIVE 主体 + DRAFT 工作快照 + 审计")
    void create_buildsActiveSoftwareWithDraft() {
        when(softwareMapper.insert(any(Software.class))).thenAnswer(inv -> {
            inv.getArgument(0, Software.class).setId(SOFTWARE_ID);
            return 1;
        });
        when(revisionMapper.insert(any(SoftwareRevision.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoftwareRevision.class).setId(REVISION_ID);
            return 1;
        });

        SoftwareCreatedVO created = service.create(USER_ID, "create-key-1");

        assertThat(created.softwareId()).isEqualTo(SOFTWARE_ID);
        assertThat(created.workingRevisionId()).isEqualTo(REVISION_ID);
        verify(publisherPolicy).requireForRestrictedWrite(USER_ID);
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(0), eq(USER_ID),
                eq("USER"), eq("CREATE"), any(), eq("DRAFT"), anyString(), any());
    }

    @Test
    @DisplayName("提交被审核词拒绝：失败不得发布（9.6），不进入 PENDING")
    void submit_rejectedBySensitiveWords() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(
                revision(SoftwareReviewStatus.DRAFT, 0, 1));
        when(releaseMapper.selectList(any())).thenReturn(java.util.List.of());
        when(revisionMediaMapper.selectCount(any())).thenReturn(0L);
        when(communitySupportFeignClient.review(any())).thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                new SoftwareReviewOutcomeVO("hash", false, false,
                        java.util.List.of(), "内容包含违规词汇，发布被拒绝", NOW)));

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, 1, "submit-key-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_REVIEW_REJECTED");
        verify(revisionMapper, never()).updateById(any(SoftwareRevision.class));
    }

    @Test
    @DisplayName("A30：publish-enabled=false 时提交直接拒绝（服务端生效，前端无法绕过）")
    void submit_publishDisabled() {
        when(softwareProperties.isPublishEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, 1, "submit-key-2"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_PUBLISH_DISABLED");
    }

    @Test
    @DisplayName("提交成功：冻结 PENDING + submitSeq+1 + 短事务锁协议（资格行→软件→快照）")
    void submit_freezesPendingUnderLocks() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(draft);
        when(releaseMapper.selectList(any())).thenReturn(java.util.List.of());
        when(revisionMediaMapper.selectCount(any())).thenReturn(0L);
        when(communitySupportFeignClient.review(any())).thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                new SoftwareReviewOutcomeVO("hash", true, false, java.util.List.of(), null, NOW)));
        when(userSupportFeignClient.profiles(any())).thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                java.util.List.of(new com.cloudmart.software.dto.software.SoftwareProfileVO(
                        USER_ID, "昵称", null, true))));

        Software lockedSoftware = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true));
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(lockedSoftware);
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(draft);

        SoftwareSubmittedVO submitted = service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, 1, "submit-key-3");

        assertThat(submitted.submitSeq()).isEqualTo(1);
        assertThat(draft.getReviewStatus()).isEqualTo(SoftwareReviewStatus.PENDING);
        assertThat(draft.getLockVersion()).isEqualTo(2);
        // 锁顺序：资格行 → 软件主行 → 快照行（4.0/9.7）
        var inOrder = org.mockito.Mockito.inOrder(lockMapper);
        inOrder.verify(lockMapper).lockPublisherByUser(USER_ID);
        inOrder.verify(lockMapper).lockSoftware(SOFTWARE_ID);
        inOrder.verify(lockMapper).lockRevision(REVISION_ID);
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(1), eq(USER_ID),
                eq("USER"), eq("SUBMIT"), eq("DRAFT"), eq("PENDING"), anyString(), any());
    }

    @Test
    @DisplayName("审核通过：切换 published 指针 + 清空 working + publicationSeq+1 + Outbox 事件")
    void reviewApprove_switchesPublishedPointer() {
        SoftwareRevision pending = revision(SoftwareReviewStatus.PENDING, 2, 2);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(pending);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true));
        Software locked = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(locked);
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(pending);

        SoftwareReviewDecisionVO decision = service.reviewDecision(ADMIN_ID, "review-key-1",
                SOFTWARE_ID, REVISION_ID, 2, 3, true, "内容合格", "req-1");

        assertThat(decision.decision()).isEqualTo("APPROVED");
        assertThat(decision.publicationSeq()).isEqualTo(2);
        assertThat(locked.getPublishedRevisionId()).isEqualTo(REVISION_ID);
        assertThat(locked.getWorkingRevisionId()).isNull();
        verify(outboxService).record(any());
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(2), eq(ADMIN_ID),
                eq("ADMIN"), eq("APPROVE"), eq("PENDING"), eq("APPROVED"), eq("内容合格"), eq("req-1"));
    }

    @Test
    @DisplayName("A07：审核旧批次（submitSeq 不匹配）409，不覆盖撤回后再次提交的数据")
    void reviewStaleSubmitSeq_conflicts() {
        SoftwareRevision pending = revision(SoftwareReviewStatus.PENDING, 5, 2);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(pending);

        assertThatThrownBy(() -> service.reviewDecision(ADMIN_ID, "review-key-2",
                SOFTWARE_ID, REVISION_ID, 2, 3, true, "ok", "req-2"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUBMIT_STALE");
        verify(revisionMapper, never()).updateById(any(SoftwareRevision.class));
    }

    @Test
    @DisplayName("A33：作者资格关闭时审核不得通过；待审稿保留")
    void reviewApprove_requiresAuthorPermission() {
        SoftwareRevision pending = revision(SoftwareReviewStatus.PENDING, 2, 2);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(pending);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(false));

        assertThatThrownBy(() -> service.reviewDecision(ADMIN_ID, "review-key-3",
                SOFTWARE_ID, REVISION_ID, 2, 3, true, "ok", "req-3"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_PUBLISH_NOT_ENABLED");
        assertThat(pending.getReviewStatus()).isEqualTo(SoftwareReviewStatus.PENDING);
        verify(revisionMapper, never()).updateById(any(SoftwareRevision.class));
    }

    @Test
    @DisplayName("A10/4.2：BANNED 软件不能 relist；UNLISTED 且有已审快照可恢复")
    void relist_lifecycleGuards() {
        Software banned = software(SoftwareLifecycle.BANNED, REVISION_ID, null);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(banned);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(banned);

        assertThatThrownBy(() -> service.relist(USER_ID, SOFTWARE_ID, 3))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_BANNED");

        Software unlisted = software(SoftwareLifecycle.UNLISTED, REVISION_ID, null);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(unlisted);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(unlisted);
        SoftwareRevision approved = revision(SoftwareReviewStatus.APPROVED, 1, 1);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(approved);

        service.relist(USER_ID, SOFTWARE_ID, 3);

        assertThat(unlisted.getLifecycle()).isEqualTo(SoftwareLifecycle.ACTIVE);
        verify(auditService).record(eq(SOFTWARE_ID), eq(null), eq(null), eq(USER_ID),
                eq("USER"), eq("RELIST"), eq("UNLISTED"), eq("ACTIVE"), anyString(), any());
    }

    @Test
    @DisplayName("A08：expectedVersion 不匹配 → SOFTWARE_VERSION_CONFLICT（并发保存仅一方成功）")
    void saveDraft_versionConflict() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 5);
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(draft);

        assertThatThrownBy(() -> service.saveDraft(USER_ID, SOFTWARE_ID, REVISION_ID, 4,
                new com.cloudmart.software.dto.software.SoftwareSnapshotInput()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_VERSION_CONFLICT");
    }

    @Test
    @DisplayName("归属防线：非本人软件一律 SOFTWARE_NOT_FOUND（不泄露存在性）")
    void ownedSoftware_rejectsOthers() {
        Software others = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        others.setOwnerId(999L);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(others);

        assertThatThrownBy(() -> service.editor(USER_ID, SOFTWARE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
    }
}
