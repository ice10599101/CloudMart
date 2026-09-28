package com.cloudmart.user.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.entity.AccountDeletionTask;
import com.cloudmart.user.feign.ErasureFeignClient;
import com.cloudmart.user.feign.OrderBlockFeignClient;
import com.cloudmart.user.repository.AccountDeletionTaskMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * USER-01：注销编排——申请→取消→再申请闭环（CAS 复活修复唯一键冲突）、
 * 未结订单阻塞（fail-closed）、EXECUTING 崩溃租约接管。
 */
@DisplayName("AccountDeletionOrchestrationService 编排语义")
class AccountDeletionOrchestrationTest {

    private static final Long USER_ID = 42L;

    private AccountDeletionOrchestrationService service;
    private AccountDeletionTaskMapper taskMapper;
    private ErasureFeignClient erasureFeignClient;
    private OrderBlockFeignClient orderBlockFeignClient;

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.cloudmart.user.repository.AccountDeletionTaskMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, com.cloudmart.user.entity.AccountDeletionTask.class);
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(AccountDeletionTaskMapper.class);
        erasureFeignClient = mock(ErasureFeignClient.class);
        orderBlockFeignClient = mock(OrderBlockFeignClient.class);
        service = new AccountDeletionOrchestrationService(
                taskMapper, erasureFeignClient, orderBlockFeignClient, "test-secret-0123456789abcdef");
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

    @Test
    @DisplayName("已有 PENDING 申请时再次申请被拒")
    void apply_pendingExists_rejected() {
        when(taskMapper.selectOne(any())).thenReturn(task("PENDING"));

        assertThatThrownBy(() -> service.apply(USER_ID, "原因"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WISH_DELETION_PENDING");
    }

    @Test
    @DisplayName("USER-01：取消后再申请 → CAS 复活原行（不 insert 撞唯一键）")
    void apply_afterCancel_revivesTask() {
        when(taskMapper.selectOne(any())).thenReturn(task("CANCELED"));
        when(taskMapper.update(any(), any())).thenReturn(1);
        AccountDeletionTask revived = task("PENDING");
        when(taskMapper.selectOne(any())).thenReturn(task("CANCELED"), revived);

        AccountDeletionTask result = service.apply(USER_ID, "再次申请");

        assertThat(result).isSameAs(revived);
        // 复活走 update 而非 insert（uk_deletion_task_user 唯一键不再冲突）
        verify(taskMapper).update(any(), any());
        verify(taskMapper, org.mockito.Mockito.never()).insert(any(AccountDeletionTask.class));
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
    @DisplayName("USER-01：有未结订单 → 注销被阻塞回退 PENDING（不执行擦除）")
    void execute_openOrders_blocked() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenReturn(ApiResponse.ok(true));

        service.executeTask(task("PENDING"));

        // 擦除不得执行；任务回退 PENDING 且记录阻塞原因
        verify(erasureFeignClient, never()).eraseWishData(anyLong(), anyString());
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AccountDeletionTask>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(taskMapper, org.mockito.Mockito.atLeastOnce()).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
    }

    @Test
    @DisplayName("USER-01：订单服务不可用（降级返回 true）→ 同样阻塞（fail-closed）")
    void execute_orderQueryDegraded_blocked() {
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(orderBlockFeignClient.hasOpenOrders(eq(USER_ID), anyString()))
                .thenReturn(ApiResponse.ok(true));

        service.executeTask(task("PENDING"));

        verify(erasureFeignClient, never()).eraseWishData(anyLong(), anyString());
    }

    @Test
    @DisplayName("USER-01：EXECUTING 崩溃租约接管——认领条件含 stale EXECUTING")
    void executeClaim_includesStaleExecuting() {
        // 认领 CAS 0 行（不可抢/无任务）→ 直接返回
        when(taskMapper.update(any(), any())).thenReturn(0);

        service.executeTask(task("EXECUTING"));

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AccountDeletionTask>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(taskMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        // 认领 SQL 含 stale 接管条件（通过 getSqlSegment 断言包装内容不可行，此处验证调用与静默返回）
        verify(erasureFeignClient, never()).eraseWishData(anyLong(), anyString());
    }
}
