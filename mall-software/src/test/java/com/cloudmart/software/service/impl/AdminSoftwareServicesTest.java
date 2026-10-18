package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminContactSaveRequest;
import com.cloudmart.software.dto.software.AdminPermissionToggleRequest;
import com.cloudmart.software.dto.software.AdminReportHandleRequest;
import com.cloudmart.software.dto.software.AdminGrantRequest;
import com.cloudmart.software.dto.software.SoftwareProfileVO;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAccessContact;
import com.cloudmart.software.entity.SoftwareAccessGrant;
import com.cloudmart.software.entity.SoftwareComment;
import com.cloudmart.software.entity.SoftwarePermissionAudit;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareReport;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReportStatus;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareAccessGrantMapper;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareLinkFeedbackMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwarePermissionAuditMapper;
import com.cloudmart.software.repository.SoftwarePublisherPermissionMapper;
import com.cloudmart.software.repository.SoftwareReportMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S09 管理端单测（A31/A32/A33/A39）：资格开关审计+幂等、授权 upsert 同行、
 * 举报处置同事务（BAN_SOFTWARE 显式封禁）、评论处置计数与状态机。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSoftwareServicesTest {

    private static final Long ADMIN_ID = 1000L;
    private static final Long USER_ID = 42L;
    private static final Long SOFTWARE_ID = 100L;

    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareReleaseMapper releaseMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareSourceMapper sourceMapper;
    @org.mockito.Mock
    private com.cloudmart.software.repository.SoftwareSourceStateMapper sourceStateMapper;
    @org.mockito.Mock
    private com.cloudmart.software.service.SoftwarePlatformActionService platformActionService;
    @org.mockito.Mock
    private com.cloudmart.software.service.SoftwareCommentStatusService commentStatusService;
    @Mock
    private SoftwarePublisherPermissionMapper publisherPermissionMapper;
    @Mock
    private SoftwareAccessGrantMapper accessGrantMapper;
    @Mock
    private SoftwareAccessContactMapper accessContactMapper;
    @Mock
    private SoftwarePermissionAuditMapper permissionAuditMapper;
    @Mock
    private SoftwareLockMapper lockMapper;
    @Mock
    private SoftwareCommentMapper commentMapper;
    @Mock
    private SoftwareReportMapper reportMapper;
    @Mock
    private SoftwareLinkFeedbackMapper feedbackMapper;
    @Mock
    private SoftwareAuditLogMapper auditLogMapper;
    @Mock
    private UserSupportFeignClient userSupportFeignClient;
    @Mock
    private SoftwareOperationService operationService;
    @Mock
    private OutboxService outboxService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AdminSoftwarePermissionServiceImpl permissionService;
    private AdminSoftwareModerationServiceImpl moderationService;
    private AdminSoftwareQueryServiceImpl queryService;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        // operationService 透传业务动作
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(6)).get())
                .when(operationService).execute(anyString(), any(), anyString(), anyString(),
                        any(), any(), any(Supplier.class), any());
        when(userSupportFeignClient.profiles(any())).thenReturn(ApiResponse.ok(
                List.of(new SoftwareProfileVO(USER_ID, "昵称", null, true))));

        permissionService = new AdminSoftwarePermissionServiceImpl(publisherPermissionMapper,
                accessGrantMapper, accessContactMapper, permissionAuditMapper, lockMapper,
                softwareMapper, revisionMapper, userSupportFeignClient, operationService,
                new ObjectMapper(), fixedClock(),
                transactionTemplate);
        moderationService = new AdminSoftwareModerationServiceImpl(softwareMapper, lockMapper, reportMapper,
                commentMapper, feedbackMapper, auditLogMapper, userSupportFeignClient,
                revisionMapper, releaseMapper, sourceMapper, sourceStateMapper,
                operationService, platformActionService, commentStatusService,
                outboxService, new ObjectMapper(), fixedClock());
        queryService = new AdminSoftwareQueryServiceImpl(softwareMapper, userSupportFeignClient,
                revisionMapper, commentMapper, auditLogMapper, releaseMapper, sourceMapper,
                operationService, commentStatusService, null, outboxService,
                new ObjectMapper(), fixedClock());
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.systemDefault());
    }

    private AdminPermissionToggleRequest toggle(boolean enable, String reason) {
        AdminPermissionToggleRequest request = new AdminPermissionToggleRequest();
        request.setEnable(enable);
        request.setReason(reason);
        return request;
    }

    @Test
    @DisplayName("A31：无记录会员开通发布资格 → 唯一键首开 + PUBLISH_ENABLE 审计")
    void toggle_firstEnableInserts() {
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(null);
        when(publisherPermissionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        permissionService.togglePermission(ADMIN_ID, USER_ID, "perm-key-1", toggle(true, "优质分享者"));

        ArgumentCaptor<SoftwarePublisherPermission> captor =
                ArgumentCaptor.forClass(SoftwarePublisherPermission.class);
        verify(publisherPermissionMapper).insert((SoftwarePublisherPermission) captor.capture());
        assertThat(captor.getValue().getCanPublish()).isTrue();
        ArgumentCaptor<SoftwarePermissionAudit> auditCaptor =
                ArgumentCaptor.forClass(SoftwarePermissionAudit.class);
        verify(permissionAuditMapper).insert((SoftwarePermissionAudit) auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("PUBLISH_ENABLE");
    }

    @Test
    @DisplayName("A33：关闭后 relist/提交被拒；开关操作记录 before/after 审计")
    void toggle_disableWritesAudit() {
        SoftwarePublisherPermission existing = new SoftwarePublisherPermission();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setCanPublish(true);
        existing.setLockVersion(0);
        when(lockMapper.lockPublisherByUser(USER_ID)).thenReturn(existing);
        when(publisherPermissionMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        permissionService.togglePermission(ADMIN_ID, USER_ID, "perm-key-2", toggle(false, "违规发布"));

        ArgumentCaptor<SoftwarePermissionAudit> auditCaptor =
                ArgumentCaptor.forClass(SoftwarePermissionAudit.class);
        verify(permissionAuditMapper).insert((SoftwarePermissionAudit) auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("PUBLISH_DISABLE");
        JsonNode before = assertValidJson(auditCaptor.getValue().getBeforeJson());
        JsonNode after = assertValidJson(auditCaptor.getValue().getAfterJson());
        assertThat(before.get("canPublish").asBoolean()).isTrue();
        assertThat(after.get("canPublish").asBoolean()).isFalse();
    }

    /**
     * 回归防线：before_json / after_json 是 MySQL json 列，非法 JSON 文本会触发
     * Data truncation 使整个管理端写操作 500。Mock mapper 吞不掉该错误，因此断言
     * 快照必须是可解析 JSON（早期实现用 "ACTIVE;expiresAt=..." 字符串拼接即在此失败）。
     */
    private JsonNode assertValidJson(String snapshot) {
        assertThat(snapshot).isNotNull();
        try {
            return new ObjectMapper().readTree(snapshot);
        } catch (JacksonException e) {
            throw new AssertionError("审计快照不是合法 JSON: " + snapshot, e);
        }
    }

    @Test
    @DisplayName("4.0：会员不可用（status≠1）→ 拒绝变更资格，不创建未知用户授权")
    void toggle_unavailableUserRejected() {
        when(userSupportFeignClient.profiles(any())).thenReturn(ApiResponse.ok(
                List.of(new SoftwareProfileVO(USER_ID, "昵称", null, false))));

        assertThatThrownBy(() -> permissionService.togglePermission(
                ADMIN_ID, USER_ID, "perm-key-3", toggle(true, "r")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_UNAVAILABLE");
        verify(publisherPermissionMapper, never()).insert(any(SoftwarePublisherPermission.class));
    }

    @Test
    @DisplayName("6.14：授权重开更新同一行（ACCESS_EXTEND 审计），不新建行")
    void grant_reopenUpdatesSameRow() {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(lockMapper.lockSoftware(SOFTWARE_ID)).thenReturn(software);
        SoftwareAccessGrant existing = new SoftwareAccessGrant();
        existing.setId(1L);
        existing.setSoftwareId(SOFTWARE_ID);
        existing.setUserId(USER_ID);
        existing.setStatus("REVOKED");
        existing.setLockVersion(2);
        when(lockMapper.lockAccessGrant(SOFTWARE_ID, USER_ID)).thenReturn(existing);

        AdminGrantRequest request = new AdminGrantRequest();
        request.setUserId(USER_ID);
        request.setReason("线下确认付款");
        permissionService.grant(ADMIN_ID, SOFTWARE_ID, "grant-key-1", request);

        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        verify(accessGrantMapper).updateById(existing);
        verify(accessGrantMapper, never()).insert(any(SoftwareAccessGrant.class));
        ArgumentCaptor<SoftwarePermissionAudit> auditCaptor =
                ArgumentCaptor.forClass(SoftwarePermissionAudit.class);
        verify(permissionAuditMapper).insert((SoftwarePermissionAudit) auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("ACCESS_EXTEND");
        JsonNode after = assertValidJson(auditCaptor.getValue().getAfterJson());
        assertThat(after.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(after.get("expiresAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("6.14：联系入口新增 → CONTACT_UPDATE 审计快照为合法 JSON（MySQL json 列回归防线）")
    void saveContact_auditSnapshotIsValidJson() {
        AdminContactSaveRequest request = new AdminContactSaveRequest();
        request.setName("客服QQ");
        request.setContactType("HTTPS_URL");
        request.setContactValue("https://example.com/support");
        request.setEnabled(true);
        request.setReason("新增入口");

        permissionService.saveContact(ADMIN_ID, "contact-key-1", request);

        ArgumentCaptor<SoftwareAccessContact> contactCaptor =
                ArgumentCaptor.forClass(SoftwareAccessContact.class);
        verify(accessContactMapper).insert((SoftwareAccessContact) contactCaptor.capture());
        ArgumentCaptor<SoftwarePermissionAudit> auditCaptor =
                ArgumentCaptor.forClass(SoftwarePermissionAudit.class);
        verify(permissionAuditMapper).insert((SoftwarePermissionAudit) auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CONTACT_UPDATE");
        JsonNode after = assertValidJson(auditCaptor.getValue().getAfterJson());
        assertThat(after.get("contactType").asString()).isEqualTo("HTTPS_URL");
        assertThat(after.get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SW09：举报处置统一走平台动作/评论状态服务；终态幂等与拒绝")
    void handleReport_explicitActions() {
        SoftwareReport report = new SoftwareReport();
        report.setId(1L);
        report.setSoftwareId(SOFTWARE_ID);
        report.setStatus(SoftwareReportStatus.OPEN);
        report.setLockVersion(0);
        when(reportMapper.selectById(1L)).thenReturn(report);

        AdminReportHandleRequest ban = new AdminReportHandleRequest();
        ban.setStatus("RESOLVED");
        ban.setAction("BAN_SOFTWARE");
        ban.setNote("恶意软件");
        moderationService.handleReport(ADMIN_ID, 1L, "report-key-1", ban);

        // 封禁委托统一平台动作服务（列表封禁与举报封禁同一状态机，事件只发一次）
        verify(platformActionService).ban(ADMIN_ID, SOFTWARE_ID, "恶意软件");
        verify(reportMapper).updateById(any(com.cloudmart.software.entity.SoftwareReport.class));

        // 举报不存在 → 明确报错，不能所有情况都 true
        when(reportMapper.selectById(99L)).thenReturn(null);
        AdminReportHandleRequest ghost = new AdminReportHandleRequest();
        ghost.setStatus("RESOLVED");
        ghost.setAction("NONE");
        ghost.setNote("x");
        assertThatThrownBy(() -> moderationService.handleReport(ADMIN_ID, 99L, "report-key-g", ghost))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");

        // 终态同意图幂等；不同意图拒绝
        SoftwareReport done = new SoftwareReport();
        done.setId(3L);
        done.setSoftwareId(SOFTWARE_ID);
        done.setStatus(SoftwareReportStatus.RESOLVED);
        done.setLockVersion(0);
        when(reportMapper.selectById(3L)).thenReturn(done);
        AdminReportHandleRequest same = new AdminReportHandleRequest();
        same.setStatus("RESOLVED");
        same.setAction("NONE");
        same.setNote("重复");
        moderationService.handleReport(ADMIN_ID, 3L, "report-key-same", same);
        AdminReportHandleRequest flip = new AdminReportHandleRequest();
        flip.setStatus("REJECTED");
        flip.setAction("NONE");
        flip.setNote("改判");
        assertThatThrownBy(() -> moderationService.handleReport(ADMIN_ID, 3L, "report-key-flip", flip))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_REPORT_TERMINAL");
    }

    @Test
    @DisplayName("SW09：评论审核走条件迁移——成功发可见事件，并发已处置返回冲突")
    void moderateComment_stateMachineAndCounting() {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(7L);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        SoftwareComment pending = new SoftwareComment();
        pending.setId(1L);
        pending.setSoftwareId(SOFTWARE_ID);
        pending.setUserId(9L);
        pending.setStatus(SoftwareCommentStatus.PENDING);
        pending.setLockVersion(0);
        when(commentMapper.selectById(1L)).thenReturn(pending);
        when(commentStatusService.transition(eq(SOFTWARE_ID), eq(1L),
                eq(SoftwareCommentStatus.PENDING), eq(SoftwareCommentStatus.VISIBLE),
                eq(1), eq(ADMIN_ID), anyString(), anyString())).thenReturn(true);

        queryService.moderateComment(ADMIN_ID, SOFTWARE_ID, 1L, "cm-key-1", "APPROVE", "内容合格");
        verify(commentStatusService).transition(eq(SOFTWARE_ID), eq(1L),
                eq(SoftwareCommentStatus.PENDING), eq(SoftwareCommentStatus.VISIBLE),
                eq(1), eq(ADMIN_ID), eq("COMMENT_APPROVE"), eq("内容合格"));
        verify(outboxService).record(any());

        when(commentStatusService.transition(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any(), anyString(), anyString()))
                .thenReturn(false);
        assertThatThrownBy(() -> queryService.moderateComment(ADMIN_ID, SOFTWARE_ID, 1L,
                "cm-key-2", "HIDE", "违规"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_STATE_CONFLICT");
    }
    @Test
    @DisplayName("adminDetail：发布者昵称按软件所有者查询并写入 editorNickname")
    void adminDetail_fillsOwnerNickname() {
        com.cloudmart.software.entity.Software software =
                new com.cloudmart.software.entity.Software();
        software.setId(112L);
        software.setOwnerId(USER_ID);
        software.setLifecycle(com.cloudmart.software.enums.SoftwareLifecycle.ACTIVE);
        software.setPublishedRevisionId(15L);
        software.setWorkingRevisionId(null);
        when(softwareMapper.selectById(112L)).thenReturn(software);

        com.cloudmart.software.entity.SoftwareRevision revision =
                new com.cloudmart.software.entity.SoftwareRevision();
        revision.setId(15L);
        revision.setSoftwareId(112L);
        revision.setEditorId(USER_ID);
        revision.setName("测试软件");
        revision.setDescriptionHtml("<p>介绍</p>");
        revision.setReviewStatus(com.cloudmart.software.enums.SoftwareReviewStatus.APPROVED);
        when(revisionMapper.selectById(15L)).thenReturn(revision);
        when(releaseMapper.selectList(any())).thenReturn(java.util.List.of());
        when(auditLogMapper.selectList(any())).thenReturn(java.util.List.of());

        when(userSupportFeignClient.profiles(any())).thenReturn(ApiResponse.ok(
                List.of(new SoftwareProfileVO(USER_ID, "ice", null, true))));

        Map<String, Object> detail = queryService.adminDetail(112L);
        assertThat(detail.get("editorNickname")).isEqualTo("ice");
        assertThat(detail.get("descriptionHtml")).isNotNull();
    }

}
