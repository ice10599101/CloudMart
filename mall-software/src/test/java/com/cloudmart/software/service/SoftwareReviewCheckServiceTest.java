package com.cloudmart.software.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.dto.software.SoftwareProfileVO;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareReviewCheckTask;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareReviewCheckTaskMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SW04 自动检查执行器：冻结内容摘要校验、通过/拒绝/临时失败三条路径，
 * 以及租约 fencing 下的终态回写。远程审核不可用绝不能伪装成通过。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareReviewCheckServiceTest {

    private static final Long SOFTWARE_ID = 100L;
    private static final Long REVISION_ID = 501L;
    private static final Long USER_ID = 42L;
    private static final Long TASK_ID = 9001L;
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    private SoftwareReviewCheckTaskMapper taskMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareRevisionMediaMapper revisionMediaMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareLockMapper lockMapper;
    @Mock
    private SoftwareApprovalService approvalService;
    @Mock
    private SoftwareAuditService auditService;
    @Mock
    private CommunitySupportFeignClient communitySupportFeignClient;
    @Mock
    private UserSupportFeignClient userSupportFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final Clock clock = Clock.fixed(NOW, ZoneId.systemDefault());
    private final LocalDateTime now = LocalDateTime.now(clock);

    private SoftwareReviewCheckService service;
    private SoftwareRevision revision;
    private Software software;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(taskMapper.findDue(any())).thenReturn(task(false, 0));
        when(taskMapper.claim(anyLong(), anyString(), any(), anyInt(), any())).thenReturn(1);
        when(taskMapper.finish(anyLong(), anyString(), anyInt(), any(), any(), any(),
                anyInt(), anyString(), any())).thenReturn(1);
        revision = revision(SoftwareReviewStatus.CHECKING);
        software = software(USER_ID);
        when(revisionMapper.selectById(REVISION_ID)).thenReturn(revision);
        when(releaseMapper.selectList(any())).thenReturn(List.of());
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(software);
        when(lockMapper.lockRevision(REVISION_ID)).thenReturn(revision);
        when(userSupportFeignClient.profiles(any())).thenReturn(ApiResponse.ok(
                List.of(new SoftwareProfileVO(USER_ID, "昵称", null, true))));
        when(revisionMediaMapper.selectCount(any())).thenReturn(0L);

        service = new SoftwareReviewCheckService(taskMapper, revisionMapper, releaseMapper,
                sourceMapper, revisionMediaMapper, softwareMapper, lockMapper,
                approvalService, auditService, communitySupportFeignClient,
                userSupportFeignClient, clock, transactionTemplate);
    }

    private SoftwareRevision revision(SoftwareReviewStatus status) {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(REVISION_ID);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setEditorId(USER_ID);
        revision.setReviewStatus(status);
        revision.setName("示例工具");
        revision.setContentDigest(SoftwareReviewTextDigest.digest(List.of("示例工具")));
        return revision;
    }

    private Software software(Long ownerId) {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(ownerId);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        return software;
    }

    private SoftwareReviewCheckTask task(boolean autoPublish, int attempts) {
        SoftwareReviewCheckTask task = new SoftwareReviewCheckTask();
        task.setId(TASK_ID);
        task.setSoftwareId(SOFTWARE_ID);
        task.setRevisionId(REVISION_ID);
        task.setOperationKey("submit-key");
        task.setContentDigest(SoftwareReviewTextDigest.digest(List.of("示例工具")));
        task.setAutoPublish(autoPublish);
        task.setStatus("PENDING");
        task.setAttempts(attempts);
        task.setLeaseVersion(3);
        return task;
    }

    private SoftwareReviewOutcomeVO outcome(boolean approved, boolean manual) {
        return new SoftwareReviewOutcomeVO("hash", approved, manual, List.of(), null, NOW);
    }

    private SoftwarePublisherPermission permission(boolean canPublish, boolean reviewRequired) {
        SoftwarePublisherPermission permission = new SoftwarePublisherPermission();
        permission.setUserId(USER_ID);
        permission.setCanPublish(canPublish);
        permission.setReviewRequired(reviewRequired);
        return permission;
    }

    @Test
    @DisplayName("SW04：检查通过且需要人工复核 → PENDING，任务 DONE")
    void passMovesToPending() {
        when(communitySupportFeignClient.review(any()))
                .thenReturn(ApiResponse.ok(outcome(true, true)));

        assertThat(service.runOnce()).isTrue();

        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.PENDING);
        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), eq(0L), eq("SYSTEM"),
                eq("AUTO_CHECK_PASS"), eq("CHECKING"), eq("PENDING"), anyString(), isNull());
        verify(taskMapper).finish(eq(TASK_ID), eq("DONE"), eq(0), isNull(), isNull(),
                eq(now), eq(4), anyString(), eq(now));
    }

    @Test
    @DisplayName("SW04：直接发布权限且检查通过 → 委托审批服务自动上线")
    void passWithDirectPermissionAutoPublishes() {
        when(taskMapper.findDue(any())).thenReturn(task(true, 0));
        when(lockMapper.lockPublisherByUser(USER_ID))
                .thenReturn(permission(true, false));
        when(communitySupportFeignClient.review(any()))
                .thenReturn(ApiResponse.ok(outcome(true, false)));

        assertThat(service.runOnce()).isTrue();

        verify(approvalService).publishApproved(software, revision, 0L, "SYSTEM",
                "AUTO_PUBLISH", "CHECKING", "自动检查通过（直接发布权限）", null);
        verify(taskMapper).finish(eq(TASK_ID), eq("DONE"), eq(0), isNull(), isNull(),
                eq(now), eq(4), anyString(), eq(now));
    }

    @Test
    @DisplayName("SW04：提交后资格被收回 → 不自动上线，退回 PENDING 人工审核")
    void autoPublishFallsBackToPendingWhenPermissionChanged() {
        when(taskMapper.findDue(any())).thenReturn(task(true, 0));
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(permission(true, true));
        when(communitySupportFeignClient.review(any()))
                .thenReturn(ApiResponse.ok(outcome(true, false)));

        assertThat(service.runOnce()).isTrue();

        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.PENDING);
        verify(approvalService, never()).publishApproved(
                any(), any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("SW04：检查拒绝 → 委托审批服务生成 REJECTED + 新 DRAFT，任务 REJECTED")
    void rejectDelegatesToApprovalService() {
        doAnswer(invocation -> {
            SoftwareRevision target = invocation.getArgument(1);
            target.setReviewStatus(SoftwareReviewStatus.REJECTED);
            return null;
        }).when(approvalService).rejectAndClone(any(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), any());
        when(communitySupportFeignClient.review(any()))
                .thenReturn(ApiResponse.ok(outcome(false, false)));

        assertThat(service.runOnce()).isTrue();

        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.REJECTED);
        verify(approvalService).rejectAndClone(software, revision, 0L, "SYSTEM",
                "AUTO_REJECT", "CHECKING", "自动检查未通过：内容审核未通过", null);
        verify(taskMapper).finish(eq(TASK_ID), eq("REJECTED"), eq(0), isNull(), isNull(),
                eq(now), eq(4), anyString(), eq(now));
    }

    @Test
    @DisplayName("SW04：账号不可用 → 直接拒绝，不进入内容审核")
    void unavailableAccountRejects() {
        when(userSupportFeignClient.profiles(any())).thenReturn(ApiResponse.ok(
                List.of(new SoftwareProfileVO(USER_ID, "昵称", null, false))));

        service.runOnce();

        verify(communitySupportFeignClient, never()).review(any());
        verify(approvalService).rejectAndClone(any(), eq(revision), eq(0L), eq("SYSTEM"),
                eq("AUTO_REJECT"), eq("CHECKING"), anyString(), isNull());
    }

    @Test
    @DisplayName("SW04：审核服务异常保持 CHECKING，按 30s 退避重试，绝不伪通过")
    void transientFailureRetriesWithBackoff() {
        when(communitySupportFeignClient.review(any()))
                .thenThrow(new IllegalStateException("timeout"));

        service.runOnce();

        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.CHECKING);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(taskMapper).finish(eq(TASK_ID), eq("PENDING"), eq(1),
                eq(now.plusSeconds(30)), error.capture(), isNull(), eq(4), anyString(), eq(now));
        assertThat(error.getValue()).contains("timeout");
        verify(approvalService, never()).publishApproved(
                any(), any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("SW04：空审核响应同样按临时故障重试")
    void nullOutcomeRetries() {
        when(communitySupportFeignClient.review(any()))
                .thenReturn(ApiResponse.<SoftwareReviewOutcomeVO>ok(null));

        service.runOnce();

        verify(taskMapper).finish(eq(TASK_ID), eq("PENDING"), eq(1),
                eq(now.plusSeconds(30)), anyString(), isNull(), eq(4), anyString(), eq(now));
    }

    @Test
    @DisplayName("SW04：重试达到上限转 DEAD_LETTER，保留最后错误")
    void exhaustedAttemptsDeadLetter() {
        when(taskMapper.findDue(any())).thenReturn(task(false, 5));
        when(communitySupportFeignClient.review(any()))
                .thenThrow(new IllegalStateException("timeout"));

        service.runOnce();

        verify(taskMapper).finish(eq(TASK_ID), eq("DEAD_LETTER"), eq(6), isNull(),
                anyString(), eq(now), eq(4), anyString(), eq(now));
        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.CHECKING);
    }

    @Test
    @DisplayName("SW04：冻结内容摘要不一致转 DEAD_LETTER，不自动通过也不自动拒绝")
    void digestMismatchDeadLetters() {
        revision.setDescriptionText("提交后被改动的介绍");

        service.runOnce();

        verify(communitySupportFeignClient, never()).review(any());
        verify(taskMapper).finish(eq(TASK_ID), eq("DEAD_LETTER"), eq(0), isNull(),
                anyString(), eq(now), eq(4), anyString(), eq(now));
        assertThat(revision.getReviewStatus()).isEqualTo(SoftwareReviewStatus.CHECKING);
    }

    @Test
    @DisplayName("SW04：提交已撤回/被处理（非 CHECKING）→ 任务 CANCELLED，不产生审核结论")
    void nonCheckingRevisionCancelsTask() {
        revision.setReviewStatus(SoftwareReviewStatus.WITHDRAWN);

        service.runOnce();

        verify(communitySupportFeignClient, never()).review(any());
        verify(taskMapper).finish(eq(TASK_ID), eq("CANCELLED"), eq(0), isNull(),
                anyString(), eq(now), eq(4), anyString(), eq(now));
    }

    @Test
    @DisplayName("SW04：租约竞争失败不执行任务，但本轮仍报告有到期任务")
    void leaseLostSkipsProcessing() {
        when(taskMapper.claim(anyLong(), anyString(), any(), anyInt(), any())).thenReturn(0);

        assertThat(service.runOnce()).isTrue();

        verify(communitySupportFeignClient, never()).review(any());
        verify(taskMapper, never()).finish(anyLong(), anyString(), anyInt(), any(),
                any(), any(), anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("SW04：无到期任务时返回 false，不领取不执行")
    void noDueTaskReturnsFalse() {
        when(taskMapper.findDue(any())).thenReturn(null);

        assertThat(service.runOnce()).isFalse();

        verify(taskMapper, never()).claim(anyLong(), anyString(), any(), anyInt(), any());
    }
}
