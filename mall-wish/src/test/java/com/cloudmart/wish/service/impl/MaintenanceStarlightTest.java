package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.WishOperation;
import com.cloudmart.wish.entity.WishResourceLog;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.repository.WishFulfillmentMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishOperationMapper;
import com.cloudmart.wish.repository.WishResourceLogMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.MaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B06 衰减与对账测试（T10/T11 语义）：条件更新 0 行不写流水；
 * 唯一业务键同日重复执行只扣一次；对账只出差异工单不改余额。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("星光衰减与对账（B06）")
class MaintenanceStarlightTest {

    private static final Long USER = 42L;

    @Mock
    private WishUserStatMapper userStatMapper;
    @Mock
    private WishResourceLogMapper resourceLogMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishFulfillmentMapper fulfillmentMapper;
    @Mock
    private WishOperationMapper operationMapper;

    private MaintenanceServiceImpl maintenanceService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        maintenanceService = new MaintenanceServiceImpl(userStatMapper, resourceLogMapper,
                wishMapper, fulfillmentMapper, new WishOperationExecutor(operationMapper, txTemplate));
        when(operationMapper.insert(any(WishOperation.class))).thenReturn(1);
    }

    private WishUserStat stat(int balance) {
        WishUserStat stat = new WishUserStat();
        stat.setUserId(USER);
        stat.setStarlightBalance(balance);
        stat.setLastActiveAt(LocalDateTime.now().minusDays(60));
        stat.setIsRestricted(false);
        return stat;
    }

    @Test
    @DisplayName("衰减：条件更新 1 行才写 DECAY 流水，余额快照为扣减后值")
    void decayWritesFlowOnlyWhenAffected() {
        when(userStatMapper.selectList(any())).thenReturn(List.of(stat(20)), List.of());
        when(userStatMapper.update(any(), any())).thenReturn(1);
        when(userStatMapper.selectById(USER)).thenReturn(stat(18));

        MaintenanceService.MapResult result = maintenanceService.starlightDecay();

        assertThat(result.processed()).isEqualTo(1);
        ArgumentCaptor<WishResourceLog> captor = ArgumentCaptor.forClass(WishResourceLog.class);
        verify(resourceLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getDelta()).isEqualTo(-2);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(18);
        assertThat(captor.getValue().getSource()).isEqualTo("DECAY");
        // 操作行含唯一业务键：userId:platformDate
        ArgumentCaptor<WishOperation> opCaptor = ArgumentCaptor.forClass(WishOperation.class);
        verify(operationMapper).insert(opCaptor.capture());
        assertThat(opCaptor.getValue().getRequestKey()).startsWith(USER + ":");
    }

    @Test
    @DisplayName("条件更新 0 行（并发已衰减/恢复活跃）：不写流水、不假成功")
    void affectedZero_noFakeFlow() {
        when(userStatMapper.selectList(any())).thenReturn(List.of(stat(20)), List.of());
        when(userStatMapper.update(any(), any())).thenReturn(0);

        MaintenanceService.MapResult result = maintenanceService.starlightDecay();

        assertThat(result.processed()).isZero();
        verify(resourceLogMapper, never()).insert(any(WishResourceLog.class));
    }

    @Test
    @DisplayName("同日重复调度：唯一业务键重放原结果，钱包只扣一次（T10）")
    void sameDayRepeat_decaysOnce() {
        when(userStatMapper.selectList(any())).thenReturn(List.of(stat(20)), List.of());
        when(userStatMapper.update(any(), any())).thenReturn(1);
        when(userStatMapper.selectById(USER)).thenReturn(stat(18));

        maintenanceService.starlightDecay();

        // 捕获第一次提交的操作行，第二次同键调用命中唯一键
        ArgumentCaptor<WishOperation> committed = ArgumentCaptor.forClass(WishOperation.class);
        verify(operationMapper).updateById(committed.capture());
        WishOperation row = committed.getValue();
        row.setStatus("COMPLETED");
        row.setResponseJson("true");

        // 第二次：候选集再次命中（调度重复触发），insert 冲突 → 重放
        when(userStatMapper.selectList(any())).thenReturn(List.of(stat(20)), List.of());
        when(operationMapper.insert(any(WishOperation.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(operationMapper.selectOne(any())).thenReturn(row);

        maintenanceService.starlightDecay();

        // 条件更新与流水只发生一次
        verify(userStatMapper, times(1)).update(any(), any());
        verify(resourceLogMapper, times(1)).insert(any(WishResourceLog.class));
    }

    @Test
    @DisplayName("对账 CHECK_ONLY：差异只进工单，绝不改余额（T11）")
    void reconcile_reportsDifferencesWithoutWrites() {
        when(userStatMapper.selectList(any())).thenReturn(List.of(stat(100)), List.of());
        // 流水求和 95 ≠ 余额 100 → 差异工单
        when(resourceLogMapper.selectMaps(any())).thenReturn(List.of(
                java.util.Map.of("user_id", (Object) USER, "total", (Object) 95L)));

        MaintenanceService.MapResult result = maintenanceService.starlightReconcile();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(userStatMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("对账一致：无差异、无写操作")
    void reconcile_consistent_noDiff() {
        when(userStatMapper.selectList(any())).thenReturn(List.of(stat(100)), List.of());
        when(resourceLogMapper.selectMaps(any())).thenReturn(List.of(
                java.util.Map.of("user_id", (Object) USER, "total", (Object) 100L)));

        MaintenanceService.MapResult result = maintenanceService.starlightReconcile();

        assertThat(result.failed()).isZero();
        verify(userStatMapper, never()).update(any(), any());
        verify(operationMapper, never()).insert(any(WishOperation.class));
        verify(resourceLogMapper, never()).insert(any(WishResourceLog.class));
        verify(userStatMapper, never()).selectById(anyLong());
    }
}
