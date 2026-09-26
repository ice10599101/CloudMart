package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishOperation;
import com.cloudmart.wish.repository.WishOperationMapper;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B04 持久幂等执行器测试（T07/T08 语义）：
 * 同键同摘要重放、同键异摘要 409、无已提交结果 409 可重试、失败不落凭证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WishOperationExecutor 持久幂等")
class WishOperationExecutorTest {

    @Mock
    private WishOperationMapper operationMapper;

    private WishOperationExecutor executor;

    private final AtomicInteger businessCalls = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        executor = new WishOperationExecutor(operationMapper, txTemplate);
        when(operationMapper.insert(any(WishOperation.class))).thenReturn(1);
    }

    private String execute(String key) {
        return executor.execute("USER", 42L, null, "ASSET_EXCHANGE", key,
                java.util.Map.of("assetId", 7L), String.class,
                () -> "result-" + businessCalls.incrementAndGet());
    }

    @Test
    @DisplayName("首次执行：业务运行、操作行置 COMPLETED 并落结果 JSON")
    void firstExecution_completes() {
        String result = execute("key-a");

        assertThat(result).isEqualTo("result-1");
        ArgumentCaptor<WishOperation> captor = ArgumentCaptor.forClass(WishOperation.class);
        verify(operationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getResponseJson()).contains("result-1");
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("同键同摘要：重放第一次已提交结果，业务不再执行")
    void sameKeySameHash_replays() {
        // 第一次提交的行
        ArgumentCaptor<WishOperation> first = ArgumentCaptor.forClass(WishOperation.class);
        execute("key-b");
        verify(operationMapper).updateById(first.capture());
        WishOperation committed = first.getValue();
        committed.setStatus("COMPLETED");

        // 第二次：键冲突 → 读到已提交行 → 摘要一致 → 重放
        when(operationMapper.insert(any(WishOperation.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(operationMapper.selectOne(any())).thenReturn(committed);

        businessCalls.set(0);
        String replayed = execute("key-b");

        assertThat(replayed).isEqualTo("result-1");
        assertThat(businessCalls.get()).isZero();
        verify(operationMapper, times(2)).insert(any(WishOperation.class));
        // 重放路径不写更新（原行已是 COMPLETED）
        verify(operationMapper, times(1)).updateById(any(WishOperation.class));
    }

    @Test
    @DisplayName("同键异摘要：409 IDEMPOTENCY_KEY_REUSED，业务不执行")
    void sameKeyDifferentPayload_conflict() {
        WishOperation committed = new WishOperation();
        committed.setRequestHash("different-hash");
        committed.setStatus("COMPLETED");
        when(operationMapper.insert(any(WishOperation.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(operationMapper.selectOne(any())).thenReturn(committed);

        assertThatThrownBy(() -> execute("key-c"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(WishErrorCodes.IDEMPOTENCY_KEY_REUSED));
        assertThat(businessCalls.get()).isZero();
    }

    @Test
    @DisplayName("键冲突但已提交行不存在（回滚竞态）：WISH_OPERATION_IN_PROGRESS")
    void duplicateWithoutCommittedRow_inProgress() {
        when(operationMapper.insert(any(WishOperation.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(operationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> execute("key-d"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(WishErrorCodes.WISH_OPERATION_IN_PROGRESS));
        assertThat(businessCalls.get()).isZero();
    }

    @Test
    @DisplayName("业务异常向外传播（真实事务中操作行随整体回滚）")
    void businessFailure_propagates() {
        assertThatThrownBy(() -> executor.execute("USER", 42L, null, "ASSET_EXCHANGE", "key-e",
                java.util.Map.of("assetId", 7L), String.class, () -> {
                    businessCalls.incrementAndGet();
                    throw new BusinessException(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT, "余额不足");
                }))
                .isInstanceOf(BusinessException.class);
        // 不写完成结果（失败不留"已成功"凭证）
        verify(operationMapper, never()).updateById(any(WishOperation.class));
    }

    @Test
    @DisplayName("空白请求键按单次请求生成（无跨重试保护但不报错）")
    void blankKey_generatesOne() {
        String result = executor.execute("USER", 42L, null, "ASSET_EXCHANGE", "  ",
                java.util.Map.of("assetId", 7L), String.class, () -> "ok");

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<WishOperation> captor = ArgumentCaptor.forClass(WishOperation.class);
        verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue().getRequestKey()).isNotBlank();
        assertThat(captor.getValue().getRequestHash()).hasSize(64);
    }

    @Test
    @DisplayName("重放绑定 actor 作用域：不同 actor 同键不串结果")
    void replayBoundToActor() {
        String actorA = executor.execute("USER", 1L, null, "OP", "k",
                java.util.Map.of("x", 1), String.class, () -> "A");
        String actorB = executor.execute("USER", 2L, null, "OP", "k",
                java.util.Map.of("x", 1), String.class, () -> "B");

        assertThat(actorA).isEqualTo("A");
        assertThat(actorB).isEqualTo("B");
        verify(operationMapper, times(2)).insert(any(WishOperation.class));
    }
}
