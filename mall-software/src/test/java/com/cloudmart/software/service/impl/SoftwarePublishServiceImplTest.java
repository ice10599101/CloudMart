package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.cloudmart.software.service.SoftwareRateLimiter;
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
    private OutboxService outboxService;
    @Mock
    private com.cloudmart.software.feign.CommunitySupportFeignClient communitySupportFeignClient;
    @Mock
    private com.cloudmart.software.feign.UserSupportFeignClient userSupportFeignClient;
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
                outboxService,
                communitySupportFeignClient, userSupportFeignClient, softwareProperties,
                rateLimiter,
                org.mockito.Mockito.mock(
                        com.cloudmart.software.service.SoftwareReleaseFanoutService.class),
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

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-key-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_REJECTED");
        verify(revisionMapper, never()).updateById(any(SoftwareRevision.class));
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
    @DisplayName("选填审核文本全部留空仍审核软件名并冻结 PENDING，保持短事务锁协议")
    void submit_freezesPendingUnderLocks() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        draft.setSummary("");
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

        SoftwareSubmittedVO submitted = service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-key-3");

        assertThat(submitted.revisionId()).isEqualTo(REVISION_ID);
        assertThat(submitted.reviewStatus()).isEqualTo("PENDING");
        assertThat(draft.getReviewStatus()).isEqualTo(SoftwareReviewStatus.PENDING);
        verify(communitySupportFeignClient).review(org.mockito.ArgumentMatchers.argThat(
                request -> request.texts().equals(java.util.List.of("示例工具"))));
        // 锁顺序：资格行 → 软件主行 → 快照行（4.0/9.7）
        var inOrder = org.mockito.Mockito.inOrder(lockMapper);
        inOrder.verify(lockMapper).lockPublisherByUser(USER_ID);
        inOrder.verify(lockMapper).lockSoftware(SOFTWARE_ID);
        inOrder.verify(lockMapper).lockRevision(REVISION_ID);
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(USER_ID),
                eq("USER"), eq("SUBMIT"), eq("DRAFT"), eq("PENDING"), anyString(), any());
    }

    @Test
    @DisplayName("50000 字介绍分段送审且不丢内容，遵守社区单条 20000 字上限")
    void submit_longDescriptionRespectsReviewContract() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        String description = "a".repeat(19_999) + "😀" + "b".repeat(29_999);
        draft.setDescriptionText(description);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(draft);
        when(communitySupportFeignClient.review(any())).thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                new SoftwareReviewOutcomeVO("hash", false, false, java.util.List.of(), "拒绝", NOW)));

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-long-text"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_REJECTED");
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.cloudmart.software.dto.software.SoftwareReviewRequest.class);
        verify(communitySupportFeignClient).review(captor.capture());
        assertThat(captor.getValue().texts()).hasSize(3).allSatisfy(text -> {
            assertThat(text).isNotBlank().hasSizeLessThanOrEqualTo(20_000);
            assertThat(Character.isHighSurrogate(text.charAt(text.length() - 1))).isFalse();
        });
        assertThat(String.join("", captor.getValue().texts())).isEqualTo(description);
    }

    @Test
    @DisplayName("空草稿返回明确的提交不完整错误，不发送空审核批次")
    void submit_emptyDraftDoesNotCallReview() {
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        SoftwareRevision draft = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        draft.setName("");
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(draft);

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-empty-text"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUBMIT_INCOMPLETE");
        verify(communitySupportFeignClient, never()).review(any());
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
                SOFTWARE_ID, REVISION_ID, true, "内容合格", "req-1");

        assertThat(decision.decision()).isEqualTo("APPROVED");
        assertThat(locked.getPublishedRevisionId()).isEqualTo(REVISION_ID);
        assertThat(locked.getWorkingRevisionId()).isNull();
        // 更新发布（seq 1→2）：REVIEW_APPROVED + RELEASE_PUBLISHED（P1 订阅广播）共两条
        verify(outboxService, org.mockito.Mockito.times(2)).record(any());
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(ADMIN_ID),
                eq("ADMIN"), eq("APPROVE"), eq("PENDING"), eq("APPROVED"), eq("内容合格"), eq("req-1"));
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
    @DisplayName("SW04：同键超时重试直接返回历史结果，不重复限频/远程审核")
    void submit_replayReturnsStoredResultBeforeSideEffects() {
        Software software = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(
                revision(SoftwareReviewStatus.PENDING, 0, 0));
        when(operationService.replay(anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(java.util.Optional.of(new SoftwareSubmittedVO(
                        SOFTWARE_ID, REVISION_ID, "PENDING")));

        SoftwareSubmittedVO result = service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-key-replay");

        assertThat(result.reviewStatus()).isEqualTo("PENDING");
        verify(communitySupportFeignClient, never()).review(any());
        verify(rateLimiter, never()).allowSubmit(any());
    }

    @Test
    @DisplayName("SW04：远程审核后内容被改，冻结前摘要不一致拒绝提交")
    void submit_contentChangedBeforeFreezeRejected() {
        Software software = software(SoftwareLifecycle.ACTIVE, null, REVISION_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        SoftwareRevision snapshot = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        snapshot.setDescriptionText("审核时内容 A");
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(snapshot);
        when(releaseMapper.selectList(any())).thenReturn(java.util.List.of());
        when(revisionMediaMapper.selectCount(any())).thenReturn(0L);
        when(communitySupportFeignClient.review(any())).thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                new SoftwareReviewOutcomeVO("hash", true, false, java.util.List.of(), null, NOW)));
        when(userSupportFeignClient.profiles(any())).thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                java.util.List.of(new com.cloudmart.software.dto.software.SoftwareProfileVO(
                        USER_ID, "昵称", null, true))));
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true));
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, null, REVISION_ID));
        SoftwareRevision locked = revision(SoftwareReviewStatus.DRAFT, 0, 1);
        locked.setDescriptionText("冻结时内容 B");
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(locked);

        assertThatThrownBy(() -> service.submit(USER_ID, SOFTWARE_ID, REVISION_ID, "submit-key-race"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUBMIT_STALE");
        verify(revisionMapper, never()).updateById(any(SoftwareRevision.class));
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
