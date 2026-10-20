package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareReviewCheckTask;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.policy.SoftwarePublisherPolicy;
import com.cloudmart.software.repository.SoftwareFavoriteMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareReviewCheckTaskMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.service.SoftwareApprovalService;
import com.cloudmart.software.service.SoftwareAuditService;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.service.SoftwareReviewTextDigest;
import com.cloudmart.software.service.SoftwareSnapshotAssembler;
import com.cloudmart.software.vo.software.SoftwareCreatedVO;
import com.cloudmart.software.vo.software.SoftwareReviewDecisionVO;
import com.cloudmart.software.vo.software.SoftwareSubmittedVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * 4.1/4.2 状态机与幂等单测（S04 出口条件）：重试不重复创建（operation 拦截）、
 * 新旧快照切换正确（approve 同事务切指针/清 working/序号+1/Outbox）、
 * 旧批次 409（A07）、资格复核（A33）、BANNED 不可 relist（4.2）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwarePublishServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 在单测环境需要 TableInfo（生产由 MP 启动装配）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Software.class);
    }

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
    private SoftwareReviewCheckTaskMapper checkTaskMapper;
    @Mock
    private SoftwareApprovalService approvalService;
    @Mock
    private SoftwareProperties softwareProperties;
    @Mock
    private SoftwareRateLimiter rateLimiter;
    @Mock
    private com.cloudmart.software.service.SoftwareAssetBindingService assetBindingService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SoftwarePublishServiceImpl service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate 直接执行回调（mock 事务管理器语义）
        // 迷你事务模拟：激活同步器，回调异常时触发 afterCompletion(ROLLED_BACK)
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .setActualTransactionActive(true);
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .initSynchronization();
            try {
                return ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            } catch (RuntimeException e) {
                for (org.springframework.transaction.support.TransactionSynchronization synchronization
                        : org.springframework.transaction.support.TransactionSynchronizationManager
                        .getSynchronizations()) {
                    synchronization.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
                }
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .clearSynchronization();
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .setActualTransactionActive(false);
                throw e;
            } finally {
                if (org.springframework.transaction.support.TransactionSynchronizationManager
                        .isSynchronizationActive()) {
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .clearSynchronization();
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .setActualTransactionActive(false);
                }
            }
        });
        service = new SoftwarePublishServiceImpl(softwareMapper, revisionMapper, releaseMapper,
                sourceMapper, revisionTagMapper, revisionMediaMapper, favoriteMapper,
                org.mockito.Mockito.mock(
                        com.cloudmart.software.repository.SoftwareAccessContactMapper.class),
                lockMapper,
                publisherPolicy, assembler, operationService, auditService,
                org.mockito.Mockito.mock(
                        com.cloudmart.software.service.SoftwareReviewWorkflowService.class),
                assetBindingService,
                checkTaskMapper, approvalService, softwareProperties,
                rateLimiter,
                org.mockito.Mockito.mock(
                        com.cloudmart.software.repository.SoftwareSubscriptionMapper.class),
                new ObjectMapper(), Clock.fixed(NOW, ZoneId.systemDefault()),
                transactionTemplate);
        // operationService 透传业务动作（真实执行状态机逻辑）
        lenient().when(operationService.execute(anyString(), any(), anyString(), anyString(),
                any(), any(), any(Supplier.class), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(6)).get());
        lenient().when(softwareProperties.isPublishEnabled()).thenReturn(true);
        lenient().when(rateLimiter.allowSubmit(any())).thenReturn(true);
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
        software.setCommentCount(0);
        software.setFavoriteCount(0);
        software.setRatingCount(0);
        software.setRatingSum(0);
        software.setLinkAccessCount(0L);
        return software;
    }

    private SoftwareRevision revision(SoftwareReviewStatus status, int ignoredSubmitSeq, int ignoredLockVersion) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setEditorId(USER_ID);
        revision.setReviewStatus(status);
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
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(USER_ID),
                eq("USER"), eq("CREATE"), any(), eq("DRAFT"), anyString(), any());
    }

    private void stubSubmitLocks(SoftwareRevision draft) {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(draft);
        when(releaseMapper.selectList(any())).thenReturn(java.util.List.of());
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true));
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(draft);
    }

    @Test
    @DisplayName("A30：publish-enabled=false 时提交直接拒绝（服务端生效，前端无法绕过）")
    void submit_publishDisabled() {
        when(softwareProperties.isPublishEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-key-2"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_PUBLISH_DISABLED");
    }

    @Test
    @DisplayName("SW04：提交在锁内冻结为 CHECKING 并持久化检查任务，提交不等待远程审核")
    void submit_freezesCheckingAndEnqueuesTaskUnderLocks() {
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        draft.setSummary("");
        stubSubmitLocks(draft);

        SoftwareSubmittedVO submitted = service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-key-3");

        assertThat(submitted.revisionId()).isEqualTo(REVISION_ID);
        assertThat(submitted.reviewStatus()).isEqualTo("CHECKING");
        assertThat(submitted.operationId()).isEqualTo("submit-key-3");
        assertThat(draft.getReviewStatus()).isEqualTo(SoftwareReviewStatus.CHECKING);
        assertThat(draft.getContentDigest()).isEqualTo(
                SoftwareReviewTextDigest.digest(java.util.List.of("示例工具")));
        // 锁顺序：资格行 → 软件主行 → 快照行（4.0/9.7）
        var inOrder = org.mockito.Mockito.inOrder(lockMapper);
        inOrder.verify(lockMapper).lockPublisherByUser(USER_ID);
        inOrder.verify(lockMapper).lockSoftware(SOFTWARE_ID);
        inOrder.verify(lockMapper).lockRevision(REVISION_ID);
        org.mockito.ArgumentCaptor<SoftwareReviewCheckTask> task =
                org.mockito.ArgumentCaptor.forClass(SoftwareReviewCheckTask.class);
        verify(checkTaskMapper).insert(task.capture());
        assertThat(task.getValue().getRevisionId()).isEqualTo(REVISION_ID);
        assertThat(task.getValue().getOperationKey()).isEqualTo("submit-key-3");
        assertThat(task.getValue().getContentDigest()).isEqualTo(draft.getContentDigest());
        assertThat(task.getValue().getAutoPublish()).isFalse();
        assertThat(task.getValue().getStatus()).isEqualTo("PENDING");
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(USER_ID),
                eq("USER"), eq("SUBMIT"), eq("DRAFT"), eq("CHECKING"), anyString(), any());
    }

    @Test
    @DisplayName("SW04：直接发布权限冻结为 CHECKING 并标记 autoPublish，检查通过后才上线")
    void submit_directPermissionEnqueuesAutoPublishTask() {
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        SoftwarePublisherPermission direct = permission(true);
        direct.setReviewRequired(false);
        stubSubmitLocks(draft);
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(direct);

        SoftwareSubmittedVO submitted = service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "direct-key");

        assertThat(submitted.reviewStatus()).isEqualTo("CHECKING");
        assertThat(draft.getReviewStatus()).isEqualTo(SoftwareReviewStatus.CHECKING);
        org.mockito.ArgumentCaptor<SoftwareReviewCheckTask> task =
                org.mockito.ArgumentCaptor.forClass(SoftwareReviewCheckTask.class);
        verify(checkTaskMapper).insert(task.capture());
        assertThat(task.getValue().getAutoPublish()).isTrue();
        verify(approvalService, never()).publishApproved(
                any(), any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("提交时发布资格已关闭：锁内复核拒绝，不冻结不建任务")
    void submit_permissionRevokedBeforeCommit() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(revision(SoftwareReviewStatus.DRAFT, 0, 1));
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(false));

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "revoked-key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_PUBLISH_NOT_ENABLED");
        verify(revisionMapper, never()).updateById(any(SoftwareRevision.class));
        verify(checkTaskMapper, never()).insert(any(SoftwareReviewCheckTask.class));
    }

    @Test
    @DisplayName("空草稿返回明确的提交不完整错误，不创建检查任务")
    void submit_emptyDraftDoesNotEnqueue() {
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        draft.setName("");
        stubSubmitLocks(draft);

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-empty-text"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUBMIT_INCOMPLETE");
        verify(checkTaskMapper, never()).insert(any(SoftwareReviewCheckTask.class));
    }

    @Test
    @DisplayName("SW04：换键但同一工作副本已提交，状态机拒绝第二份检查任务")
    void submit_secondKeyAfterCheckingRejected() {
        SoftwareRevision submittedRevision = revision(SoftwareReviewStatus.CHECKING, 0, 1);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(submittedRevision);
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true));
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(submittedRevision);

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "another-key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_STATE_CONFLICT");
        verify(checkTaskMapper, never()).insert(any(SoftwareReviewCheckTask.class));
    }

    @Test
    @DisplayName("SW04：人工通过委托统一审批服务（指针切换/审计/Outbox 只在该服务内实现）")
    void reviewApprove_delegatesToApprovalService() {
        SoftwareRevision pending = revision(SoftwareReviewStatus.PENDING, 2, 2);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(pending);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true));
        Software locked = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(locked);
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(pending);
        when(approvalService.publishApproved(locked, pending, ADMIN_ID, "ADMIN", "APPROVE",
                "PENDING", "内容合格", "req-1"))
                .thenReturn(new SoftwareReviewDecisionVO(SOFTWARE_ID, REVISION_ID, "APPROVED"));

        SoftwareReviewDecisionVO decision = service.reviewDecision(ADMIN_ID, "review-key-1",
                SOFTWARE_ID, REVISION_ID, true, "内容合格", "req-1");

        assertThat(decision.decision()).isEqualTo("APPROVED");
        verify(approvalService).publishApproved(locked, pending, ADMIN_ID, "ADMIN", "APPROVE",
                "PENDING", "内容合格", "req-1");
        assertThat(pending.getReviewerId()).isEqualTo(ADMIN_ID);
        assertThat(pending.getReviewReason()).isEqualTo("内容合格");
    }

    @Test
    @DisplayName("SW04：人工拒绝不切 published 指针，委托统一审批服务生成可编辑副本")
    void reviewReject_delegatesToApprovalService() {
        SoftwareRevision pending = revision(SoftwareReviewStatus.PENDING, 2, 2);
        pending.setContentDigest(SoftwareReviewTextDigest.digest(List.of("示例工具")));
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(pending);
        Software locked = software(SoftwareLifecycle.ACTIVE, REVISION_ID, null);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(locked);
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(pending);

        SoftwareReviewDecisionVO decision = service.reviewDecision(ADMIN_ID, "review-key-reject",
                SOFTWARE_ID, REVISION_ID, false, "内容不合规", "req-reject");

        assertThat(decision.decision()).isEqualTo("REJECTED");
        verify(approvalService).rejectAndClone(locked, pending, ADMIN_ID, "ADMIN", "REJECT",
                "PENDING", "内容不合规", "req-reject");
        verify(approvalService, never()).publishApproved(
                any(), any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("SW04：撤回检查中的提交取消检查任务，保留 WITHDRAWN 并克隆新 DRAFT")
    void withdraw_checkingCancelsTaskAndClonesDraft() {
        Software software = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(software);
        SoftwareRevision checking = revision(SoftwareReviewStatus.CHECKING, 0, 1);
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(checking);
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 0);
        draft.setId(777L);
        when(assembler.cloneInto(checking, SOFTWARE_ID, USER_ID)).thenReturn(draft);
        when(revisionMapper.selectById(777L)).thenReturn(draft);

        var editor = service.withdraw(USER_ID, SOFTWARE_ID, REVISION_ID);

        assertThat(editor.revisionId()).isEqualTo(777L);
        assertThat(checking.getReviewStatus()).isEqualTo(SoftwareReviewStatus.WITHDRAWN);
        assertThat(draft.getCopiedFromRevisionId()).isEqualTo(REVISION_ID);
        assertThat(software.getWorkingRevisionId()).isEqualTo(777L);
        verify(checkTaskMapper).cancelByRevision(eq(REVISION_ID), any());
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(USER_ID),
                eq("USER"), eq("WITHDRAW"), eq("CHECKING"), eq("WITHDRAWN"), anyString(), any());
    }

    @Test
    @DisplayName("SW04：Mine 待审页签展示 CHECKING，拒绝副本保留 rejected 上下文")
    void mine_showsCheckingAndRejectedCopies() {
        Software checkingSoftware = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(softwareMapper.selectList(any())).thenReturn(List.of(checkingSoftware));
        when(revisionMapper.selectById(REVISION_ID))
                .thenReturn(revision(SoftwareReviewStatus.CHECKING, 0, 1));

        var pending = service.mine(USER_ID, "pending", 1, 10);

        assertThat(pending.getRecords()).hasSize(1);
        assertThat(pending.getRecords().get(0).status()).isEqualTo("CHECKING");

        Software rejectedSoftware = software(SoftwareLifecycle.ACTIVE, null, 900L);
        when(softwareMapper.selectList(any())).thenReturn(List.of(rejectedSoftware));
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 0);
        draft.setId(900L);
        draft.setCopiedFromRevisionId(REVISION_ID);
        when(revisionMapper.selectById(900L)).thenReturn(draft);
        SoftwareRevision rejected = revision(SoftwareReviewStatus.REJECTED, 0, 0);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(rejected);

        var rejectedTab = service.mine(USER_ID, "rejected", 1, 10);

        assertThat(rejectedTab.getRecords()).hasSize(1);
        assertThat(rejectedTab.getRecords().get(0).status()).isEqualTo("REJECTED");
        assertThat(rejectedTab.getRecords().get(0).revisionId()).isEqualTo(900L);
    }

    @Test
    @DisplayName("已完成的提交不能被旧审核页面再次决定（不覆盖已审内容）")
    void reviewCompletedSubmissionRejected() {
        SoftwareRevision approved = revision(SoftwareReviewStatus.APPROVED, 0, 0);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(approved);

        assertThatThrownBy(() -> service.reviewDecision(ADMIN_ID, "review-key-2",
                SOFTWARE_ID, REVISION_ID, true, "ok", "req-2"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
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
                SOFTWARE_ID, REVISION_ID, true, "ok", "req-3"))
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

        assertThatThrownBy(() -> service.relist(USER_ID, SOFTWARE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_BANNED");

        Software unlisted = software(SoftwareLifecycle.UNLISTED, REVISION_ID, null);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(unlisted);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(unlisted);
        SoftwareRevision approved = revision(SoftwareReviewStatus.APPROVED, 1, 1);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(approved);

        service.relist(USER_ID, SOFTWARE_ID);

        assertThat(unlisted.getLifecycle()).isEqualTo(SoftwareLifecycle.ACTIVE);
        verify(auditService).record(eq(SOFTWARE_ID), eq(null), eq(USER_ID),
                eq("USER"), eq("RELIST"), eq("UNLISTED"), eq("ACTIVE"), anyString(), any());
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

    @Test
    @DisplayName("SW04：同键超时重试直接返回历史结果，不重复限频/冻结/入队检查")
    void submit_replayReturnsStoredResultBeforeSideEffects() {
        Software software = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(
                revision(SoftwareReviewStatus.CHECKING, 0, 0));
        when(operationService.replay(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(java.util.Optional.of(new SoftwareSubmittedVO(
                        SOFTWARE_ID, REVISION_ID, "CHECKING", "submit-key-replay")));

        SoftwareSubmittedVO result = service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-key-replay");

        assertThat(result.reviewStatus()).isEqualTo("CHECKING");
        assertThat(result.operationId()).isEqualTo("submit-key-replay");
        verify(rateLimiter, never()).allowSubmit(any());
        verify(assetBindingService, never()).bindRevisionAssets(any(), any(), any());
        verify(checkTaskMapper, never()).insert(any(SoftwareReviewCheckTask.class));
    }

    @Test
    @DisplayName("SW04：审核时内容摘要与冻结摘要不一致拒绝通过")
    void review_contentDigestMismatchRejected() {
        SoftwareRevision pending = revision(SoftwareReviewStatus.PENDING, 0, 0);
        pending.setContentDigest("a".repeat(64));
        pending.setDescriptionText("已被篡改的内容");
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(pending);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true));
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(pending);

        assertThatThrownBy(() -> service.reviewDecision(ADMIN_ID, "review-key-digest",
                SOFTWARE_ID, REVISION_ID, true, "ok", "req-digest"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_STATE_CONFLICT");
        verify(revisionMapper, never()).updateById(any(SoftwareRevision.class));
    }

    // ------------------------------------------------------------------
    // SW08：素材绑定补偿按操作归属
    // ------------------------------------------------------------------

    private com.cloudmart.software.entity.SoftwareRevision sw08Draft(Long icon) {
        com.cloudmart.software.entity.SoftwareRevision revision =
                new com.cloudmart.software.entity.SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setReviewStatus(SoftwareReviewStatus.DRAFT);
        revision.setIconAssetId(icon);
        return revision;
    }

    private com.cloudmart.software.entity.SoftwareRevisionMedia sw08Media(long assetId) {
        com.cloudmart.software.entity.SoftwareRevisionMedia media =
                new com.cloudmart.software.entity.SoftwareRevisionMedia();
        media.setRevisionId(REVISION_ID);
        media.setAssetId(assetId);
        return media;
    }

    private com.cloudmart.software.dto.software.SoftwareSnapshotInput sw08Input(long icon, Long... screenshots) {
        com.cloudmart.software.dto.software.SoftwareSnapshotInput input =
                new com.cloudmart.software.dto.software.SoftwareSnapshotInput();
        input.setIconAssetId(icon);
        input.setScreenshots(java.util.Arrays.stream(screenshots).map(a -> {
            com.cloudmart.software.dto.software.SoftwareSnapshotInput.MediaInput media =
                    new com.cloudmart.software.dto.software.SoftwareSnapshotInput.MediaInput();
            media.setAssetId(a);
            return media;
        }).toList());
        return input;
    }

    @Test
    @DisplayName("SF08：从未发布软件恢复历史——先建空 DRAFT 工作副本，published 指针不动")
    void draftFromHistory_neverPublishedCreatesRecoveryDraft() {
        Software software = software(SoftwareLifecycle.ACTIVE, null, null);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        SoftwareRevision history = revision(SoftwareReviewStatus.REJECTED, 0, 0);
        history.setId(300L);
        history.setSubmittedAt(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        when(revisionMapper.selectById(300L)).thenReturn(history);

        Software locked = software(SoftwareLifecycle.ACTIVE, null, null);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(locked);
        SoftwareRevision editTarget = revision(SoftwareReviewStatus.DRAFT, 0, 0);
        editTarget.setId(800L);
        when(lockMapper.lockRevision(800L)).thenReturn(editTarget);
        when(revisionMapper.insert(any(SoftwareRevision.class))).thenAnswer(inv -> {
            SoftwareRevision draft = inv.getArgument(0);
            draft.setId(800L);
            when(revisionMapper.selectById(800L)).thenReturn(draft);
            return 1;
        });

        var editor = service.draftFromHistory(USER_ID, SOFTWARE_ID, 300L, "history-key");

        assertThat(editor.revisionId()).isEqualTo(800L);
        assertThat(locked.getWorkingRevisionId()).isEqualTo(800L);
        assertThat(software.getPublishedRevisionId()).isNull();
        org.mockito.ArgumentCaptor<SoftwareRevision> inserted =
                org.mockito.ArgumentCaptor.forClass(SoftwareRevision.class);
        verify(revisionMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getReviewStatus()).isEqualTo(SoftwareReviewStatus.DRAFT);
        assertThat(inserted.getValue().getEditorId()).isEqualTo(USER_ID);
        verify(revisionMapper).updateById(
                org.mockito.ArgumentMatchers.<SoftwareRevision>argThat(row -> Long.valueOf(300L)
                        .equals(row.getCopiedFromRevisionId())));
        verify(operationService).execute(eq("USER"), eq(USER_ID),
                eq(SoftwareOperationService.OP_RECOVERY_DRAFT), eq("history-key"), any(),
                eq(SOFTWARE_ID), any(Supplier.class), any());
        verify(auditService).record(eq(SOFTWARE_ID), eq(800L), eq(USER_ID), eq("USER"),
                eq("RECOVERY_DRAFT"), org.mockito.ArgumentMatchers.isNull(), eq("DRAFT"),
                anyString(), any());
    }

    @Test
    @DisplayName("SW08：本地保存失败只对本次新增资产排回滚解绑，原有图标/截图不被误拆")
    void save_rollbackUnbindsOnlyAddedAssets() {
        Software software = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(sw08Draft(1L));
        when(revisionMediaMapper.selectList(any()))
                .thenReturn(List.of(sw08Media(2L), sw08Media(3L)));
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(sw08Draft(1L));
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(assembler).applyInput(any(), any());

        assertThatThrownBy(() -> service.saveDraft(USER_ID, SOFTWARE_ID, REVISION_ID,
                sw08Input(1L, 3L, 9L)))
                .isInstanceOf(IllegalStateException.class);

        org.mockito.ArgumentCaptor<String> opId =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<List<Long>> assets =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<String> kind =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(assetBindingService, org.mockito.Mockito.times(1)).registerUnbindCompensation(
                opId.capture(), eq(REVISION_ID), assets.capture(), kind.capture());
        assertThat(kind.getValue()).isEqualTo("rollback");
        assertThat(assets.getValue()).containsExactly(9L);
        verify(assetBindingService, never()).cancelUnbindCompensation(
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("SW08：保存成功撤销回滚意图，并按提交后的引用差量释放被移除旧资产")
    void save_successCancelsRollbackAndReleasesRemoved() {
        Software software = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(revisionMapper.selectById(REVISION_ID))
                .thenReturn(sw08Draft(1L), sw08Draft(1L));
        when(revisionMediaMapper.selectList(any()))
                .thenReturn(List.of(sw08Media(2L), sw08Media(3L)))
                .thenReturn(List.of(sw08Media(3L), sw08Media(9L)));
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(sw08Draft(1L));

        service.saveDraft(USER_ID, SOFTWARE_ID, REVISION_ID, sw08Input(1L, 3L, 9L));

        org.mockito.ArgumentCaptor<String> opId =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<List<Long>> rollbackAssets =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(assetBindingService).registerUnbindCompensation(
                opId.capture(), eq(REVISION_ID), rollbackAssets.capture(), eq("rollback"));
        assertThat(rollbackAssets.getValue()).containsExactly(9L);

        verify(assetBindingService).cancelUnbindCompensation(
                eq(opId.getValue()), eq(REVISION_ID), eq(List.of(9L)), eq("rollback"));

        org.mockito.ArgumentCaptor<String> releaseOp =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<List<Long>> releaseAssets =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(assetBindingService).registerUnbindCompensation(
                releaseOp.capture(), eq(REVISION_ID), releaseAssets.capture(), eq("release"));
        assertThat(releaseOp.getValue()).isEqualTo(opId.getValue());
        assertThat(releaseAssets.getValue()).containsExactly(2L);
    }
}
