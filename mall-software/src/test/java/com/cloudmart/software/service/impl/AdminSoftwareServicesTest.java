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
                softwareMapper, userSupportFeignClient, operationService,
                new ObjectMapper(), fixedClock());
        moderationService = new AdminSoftwareModerationServiceImpl(softwareMapper, reportMapper,
                commentMapper, feedbackMapper, auditLogMapper, operationService, outboxService,
                new ObjectMapper(), fixedClock());
        queryService = new AdminSoftwareQueryServiceImpl(softwareMapper, null, commentMapper,
                auditLogMapper, operationService, outboxService, new ObjectMapper(), fixedClock());
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
    @DisplayName("A39：举报成立+BAN_SOFTWARE 显式封禁（审计+SOFTWARE_BANNED 事件）；NONE 不隐式封禁")
    void handleReport_explicitActions() {
        SoftwareReport report = new SoftwareReport();
        report.setId(1L);
        report.setSoftwareId(SOFTWARE_ID);
        report.setStatus(SoftwareReportStatus.OPEN);
        report.setLockVersion(0);
        when(reportMapper.selectById(1L)).thenReturn(report);
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(7L);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        software.setLockVersion(0);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);

        AdminReportHandleRequest request = new AdminReportHandleRequest();
        request.setStatus("RESOLVED");
        request.setAction("BAN_SOFTWARE");
        request.setNote("恶意软件");
        moderationService.handleReport(ADMIN_ID, 1L, "report-key-1", request);

        assertThat(software.getLifecycle()).isEqualTo(SoftwareLifecycle.BANNED);
        verify(outboxService).record(any());

        // NONE：仅标记成立，不隐式封禁
        SoftwareReport report2 = new SoftwareReport();
        report2.setId(2L);
        report2.setSoftwareId(SOFTWARE_ID);
        report2.setStatus(SoftwareReportStatus.OPEN);
        report2.setLockVersion(0);
        when(reportMapper.selectById(2L)).thenReturn(report2);
        AdminReportHandleRequest none = new AdminReportHandleRequest();
        none.setStatus("RESOLVED");
        none.setAction("NONE");
        none.setNote("标记成立");
        moderationService.handleReport(ADMIN_ID, 2L, "report-key-2", none);
        assertThat(software.getLifecycle()).isEqualTo(SoftwareLifecycle.BANNED); // 未再变化
    }

    @Test
    @DisplayName("6.8：评论 APPROVE 计数+1+可见事件；HIDE 计数-1；REJECT 不计数")
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

        queryService.moderateComment(ADMIN_ID, SOFTWARE_ID, 1L, "cm-key-1", "APPROVE", "内容合格");
        assertThat(pending.getStatus()).isEqualTo(SoftwareCommentStatus.VISIBLE);
        verify(softwareMapper).incrementCommentCount(SOFTWARE_ID, 1);
        verify(outboxService).record(any());

        // HIDE：VISIBLE→HIDDEN 计数-1
        when(commentMapper.selectById(1L)).thenReturn(pending);
        queryService.moderateComment(ADMIN_ID, SOFTWARE_ID, 1L, "cm-key-2", "HIDE", "违规");
        assertThat(pending.getStatus()).isEqualTo(SoftwareCommentStatus.HIDDEN);
        verify(softwareMapper).incrementCommentCount(SOFTWARE_ID, -1);

        // REJECT 只能作用于 PENDING
        assertThatThrownBy(() -> queryService.moderateComment(ADMIN_ID, SOFTWARE_ID, 1L,
                "cm-key-3", "REJECT", "拒绝"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_STATE_CONFLICT");
    }
}
