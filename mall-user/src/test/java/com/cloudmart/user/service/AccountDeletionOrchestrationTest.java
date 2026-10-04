package com.cloudmart.user.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.entity.AccountDeletionStep;
import com.cloudmart.user.entity.AccountDeletionTask;
import com.cloudmart.user.feign.AuthStateFeignClient;
import com.cloudmart.user.feign.ErasureFeignClient;
import com.cloudmart.user.feign.OrderBlockFeignClient;
import com.cloudmart.user.feign.OrderErasureFeignClient;
import com.cloudmart.user.repository.AccountDeletionStepMapper;
import com.cloudmart.user.repository.AccountDeletionTaskMapper;
import com.cloudmart.user.repository.UserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T06 账号注销跨域可恢复流程：预检 fail-closed（远程异常/success=false 不放行）、
 * 冻结先行（会话撤销先于一切擦除）、步骤台账分域执行（未接线域如实 BLOCKED）、
 * 申请→取消→再申请闭环（CAS 复活）。
 */
@DisplayName("AccountDeletionOrchestrationService 编排语义（T06）")
class AccountDeletionOrchestrationTest {

    private static final Long USER_ID = 42L;

    private AccountDeletionOrchestrationService service;
    private AccountDeletionTaskMapper taskMapper;
    private AccountDeletionStepMapper stepMapper;
    private UserMapper userMapper;
    private ErasureFeignClient erasureFeignClient;
    private OrderErasureFeignClient orderErasureFeignClient;
    private AuthStateFeignClient authStateFeignClient;
    private OrderBlockFeignClient orderBlockFeignClient;

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        for (Class<?> clazz : List.of(AccountDeletionTask.class, AccountDeletionStep.class,
                com.cloudmart.user.entity.User.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, clazz);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        taskMapper = mock(AccountDeletionTaskMapper.class);
        stepMapper = mock(AccountDeletionStepMapper.class);
        userMapper = mock(UserMapper.class);
        erasureFeignClient = mock(ErasureFeignClient.class);
        orderErasureFeignClient = mock(OrderErasureFeignClient.class);
        authStateFeignClient = mock(AuthStateFeignClient.class);
        orderBlockFeignClient = mock(OrderBlockFeignClient.class);
        service = new AccountDeletionOrchestrationService(taskMapper, stepMapper, userMapper,
                erasureFeignClient, orderErasureFeignClient, authStateFeignClient,
                orderBlockFeignClient, "test-secret-0123456789abcdef-0123456789abcdef", "test-instance");
    }

    private AccountDeletionTask task(String status) {
        AccountDeletionTask task = new AccountDeletionTask();
        task.setId(1L);
        task.setUserId(USER_ID);
        task.setStatus(status);
        task.setRequestedAt(LocalDateTime.now(ZoneId.of("UTC")).minusDays(40));
        task.setExecuteAfter(LocalDateTime.now(ZoneId.of("UTC")).minusDays(10));
        return task;
    }

    /** 台账桩：任意步骤可认领，返回对应实体 */
    private AccountDeletionStep step(String domain, String name) {
        AccountDeletionStep step = new AccountDeletionStep();
        step.setId((long) (domain + name).hashCode());
        step.setTaskId(1L);
        step.setUserId(USER_ID);
        step.setDomain(domain);
        step.setStep(name);
        step.setStatus(AccountDeletionStep.STATUS_PENDING);
        step.setAttempts(0);
        return step;
    }

    private void stubClaimableSteps() {
        // selectOne 按认领顺序出队（freezeSessions 先 AUTH；executeSteps 按 STEP_SPECS 序）；
        // selectList 返回冻结后待执行的 5 步（AUTH 已在冻结步完成）
        when(stepMapper.selectOne(any())).thenReturn(
                step("AUTH", "SESSION_REVOKE"),
                step("ORDER", "OPEN_ORDER_CHECK"),
                step("WISH", "ERASE"),
                step("ORDER", "ANONYMIZE"),
                step("USER", "ANONYMIZE"),
                step("COMMUNITY", "ERASE"));
        when(stepMapper.claim(anyLong(), anyString(), any())).thenReturn(1);
        when(stepMapper.markSuccess(anyLong(), anyString())).thenReturn(1);
        when(stepMapper.markFailed(anyLong(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(stepMapper.selectList(any())).thenReturn(List.of(
                step("ORDER", "OPEN_ORDER_CHECK"),
                step("WISH", "ERASE"), step("ORDER", "ANONYMIZE"),
                step("USER", "ANONYMIZE"), step("COMMUNITY", "ERASE")));
    }

    @Test
    @DisplayName("已有 PENDING 申请时再次申请被拒")
    void apply_pendingExists_rejected() {
        when(taskMapper.selectOne(any())).thenReturn(task("PENDING"));

        assertThatThrownBy(() -> service.apply(USER_ID, "原因"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WISH_DELETION_PENDING");
    }

    @Test
    @DisplayName("USER-01：取消后再申请 → CAS 复活原行并重建步骤台账")
    void apply_afterCancel_revivesTask() {
        AccountDeletionTask revived = task("PENDING");
        when(taskMapper.selectOne(any())).thenReturn(task("CANCELED"), revived);
        when(taskMapper.update(any(), any())).thenReturn(1);

        AccountDeletionTask result = service.apply(USER_ID, "再次申请");

        assertThat(result).isSameAs(revived);
        verify(taskMapper).update(any(), any());
        verify(taskMapper, never()).insert(any(AccountDeletionTask.class));
        // T06：台账重建（delete + 8 域 insert）
        verify(stepMapper).delete(any());
        verify(stepMapper, Mockito.times(8)).insert(any(AccountDeletionStep.class));
    }

    @Test
    @DisplayName("复活 CAS 失败（并发他人已激活）→ 冲突错误")
    void apply_reviveConflict_throws() {
        when(taskMapper.selectOne(any())).thenReturn(task("CANCELED"));
        when(taskMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.apply(USER_ID, "原因"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WISH_STATUS_CONFLICT");
    }

    @Test
    @DisplayName("T06 预检 fail-closed：有未结订单 → BLOCKED，不执行任何擦除")
    void precheck_openOrders_blocked() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenReturn(ApiResponse.ok(true));

        service.executeTask(task("PENDING"));

        verify(erasureFeignClient, never()).eraseWishData(anyLong(), anyString());
        verify(authStateFeignClient, never()).invalidateState(any());
    }

    @Test
    @DisplayName("T06 预检 fail-closed：订单服务 success=false（业务失败响应）不放行（旧实现缺陷）")
    void precheck_businessFailureResponse_blocked() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenReturn(ApiResponse.fail("ORDER_INTERNAL_ERROR", "服务内部错误"));

        service.executeTask(task("PENDING"));

        verify(erasureFeignClient, never()).eraseWishData(anyLong(), anyString());
    }

    @Test
    @DisplayName("T06 预检 fail-closed：订单服务调用异常不放行")
    void precheck_remoteError_blocked() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenThrow(new RuntimeException("connect timeout"));

        service.executeTask(task("PENDING"));

        verify(erasureFeignClient, never()).eraseWishData(anyLong(), anyString());
        verify(authStateFeignClient, never()).invalidateState(any());
    }

    @Test
    @DisplayName("T06 冻结先行：会话撤销必须发生在任何数据擦除之前")
    void execution_freezeFirst() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenReturn(ApiResponse.ok(false));
        stubClaimableSteps();
        when(authStateFeignClient.invalidateState(any())).thenReturn(ApiResponse.ok(null));
        when(erasureFeignClient.eraseWishData(anyLong(), anyString()))
                .thenReturn(ApiResponse.ok(true));
        when(orderErasureFeignClient.anonymizeReceiver(anyLong())).thenReturn(ApiResponse.ok(3));

        service.executeTask(task("PENDING"));

        InOrder inOrder = Mockito.inOrder(authStateFeignClient, erasureFeignClient);
        inOrder.verify(authStateFeignClient).invalidateState(any());
        inOrder.verify(erasureFeignClient).eraseWishData(anyLong(), anyString());
        verify(orderErasureFeignClient).anonymizeReceiver(USER_ID);
    }

    @Test
    @DisplayName("T06 未接线域如实暴露：任务停留 BLOCKED（ERASURE_DOMAIN_NOT_WIRED），不假装完成")
    void execution_unwiredDomain_blocked() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenReturn(ApiResponse.ok(false));
        stubClaimableSteps();
        when(authStateFeignClient.invalidateState(any())).thenReturn(ApiResponse.ok(null));
        when(erasureFeignClient.eraseWishData(anyLong(), anyString()))
                .thenReturn(ApiResponse.ok(true));
        when(orderErasureFeignClient.anonymizeReceiver(anyLong())).thenReturn(ApiResponse.ok(3));

        service.executeTask(task("PENDING"));

        // COMMUNITY/NOTIFICATION/FILE 无擦除端点：任务不可 COMPLETED
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AccountDeletionTask>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(taskMapper, Mockito.atLeastOnce())
                .update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        verify(erasureFeignClient).eraseWishData(anyLong(), anyString());
    }

    @Test
    @DisplayName("T06 会话撤销失败：步骤 FAILED 退避重试，任务停留 BLOCKED 不假装完成")
    void freezeFailure_blocksExecution() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenReturn(ApiResponse.ok(false));
        stubClaimableSteps();
        when(authStateFeignClient.invalidateState(any()))
                .thenThrow(new RuntimeException("auth down"));

        service.executeTask(task("PENDING"));

        // 冻结步骤失败留痕（其余步骤独立推进/重试），任务最终 BLOCKED 而非 COMPLETED
        verify(stepMapper, Mockito.atLeastOnce())
                .markFailed(anyLong(), eq("test-instance"), eq("FAILED"), any(), any());
        verify(taskMapper, Mockito.atLeastOnce()).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    @DisplayName("USER-01：EXECUTING 崩溃租约接管——认领 CAS 0 行时静默返回")
    void executeClaim_includesStaleExecuting() {
        when(taskMapper.update(any(), any())).thenReturn(0);

        service.executeTask(task("EXECUTING"));

        verify(taskMapper).update(org.mockito.ArgumentMatchers.isNull(),
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class).capture());
        verify(erasureFeignClient, never()).eraseWishData(anyLong(), anyString());
    }

    @Test
    @DisplayName("T06 运营重试：FAILED 步骤清零退避，任务回 BLOCKED 等待扫描")
    void retryFailedSteps_resetsAndBlocks() {
        var task = task("BLOCKED");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(stepMapper.update(any(), any())).thenReturn(2);

        service.retryFailedSteps(1L, "人工排查后重试");

        verify(stepMapper).update(any(), any());
        verify(taskMapper).update(any(), any());
    }

    @Test
    @DisplayName("T06 运营重试：原因必填（审计）")
    void retryFailedSteps_reasonRequired() {
        assertThatThrownBy(() -> service.retryFailedSteps(1L, " "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }
}
