package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.feign.WishFeignClient.PetWalletOperationVO;
import com.cloudmart.pet.service.PetOperationRecoverable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P02 恢复器分支测试（TX-03/TX-04 对应单测断言）：
 * EARN 按本地事实收敛（UNKNOWN 完成 / PENDING 转人工）、退款分支与发放分支隔离、
 * CAS 未命中退出、明确拒绝的退款转人工。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetOperationRecoveryService 旧单收敛")
class PetOperationRecoveryServiceTest {

    @Mock
    private PetOperationStore operationStore;
    @Mock
    private PetOperationService operationService;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private PetOperationRecoverable spendRecoverable;

    private PetOperationRecoveryService recovery;

    @BeforeEach
    void setUp() {
        lenient().when(spendRecoverable.supportedBizType()).thenReturn("SHOP_BUY");
        recovery = new PetOperationRecoveryService(operationStore, operationService, wishFeignClient,
                List.of(spendRecoverable));
        lenient().when(operationService.refundOperationId(anyString())).thenReturn("REFUND:abc");
        lenient().when(operationStore.acquireLease(any(), any())).thenReturn(true);
    }

    private PetOperation operation(String direction, String status) {
        PetOperation operation = new PetOperation();
        operation.setId(1L);
        operation.setOperationId("op-1");
        operation.setUserId(1001L);
        operation.setBizType("QUEST_CLAIM");
        operation.setDirection(direction);
        operation.setAmount(10);
        operation.setStatus(status);
        return operation;
    }

    private PetWalletOperationVO completed(String operationId, String type, int credited) {
        return new PetWalletOperationVO(operationId, type, credited, credited, 100, "PET_REWARD",
                9L, "COMPLETED", false);
    }

    @Test
    @DisplayName("TX-03：EARN 钱包已发 + 原始 UNKNOWN（本地已按结算中提交）→ 直接完成")
    void earnWalletCompleted_fromUnknown_completes() {
        PetOperation operation = operation("EARN", "UNKNOWN");
        when(operationService.queryWallet("op-1")).thenReturn(completed("op-1", "EARN", 10));

        recovery.recoverOne(operation, false);

        verify(operationStore).markRetry(eq(operation), eq("COMPLETED"), any(), any(), any());
        verify(operationStore, never()).markRetry(eq(operation), eq("MANUAL_REVIEW"), any(), any(), any());
    }

    @Test
    @DisplayName("TX-03：EARN 钱包已发 + 原始 PENDING（本地事实未证明）→ MANUAL_REVIEW，禁止盲标完成")
    void earnWalletCompleted_fromPending_manualReview() {
        PetOperation operation = operation("EARN", "PENDING");
        when(operationService.queryWallet("op-1")).thenReturn(completed("op-1", "EARN", 10));

        recovery.recoverOne(operation, false);

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(operationStore).markRetry(eq(operation), status.capture(), any(), any(), any());
        assertThat(status.getValue()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    @DisplayName("TX-04：COMPENSATING 行只续跑退款分支，不查原单结果、不重入发放")
    void compensating_resumeRefundOnly() {
        PetOperation operation = operation("EARN", "COMPENSATING");
        when(wishFeignClient.refundStarlightIdempotent(eq(1001L), eq(10), eq("op-1"), eq("REFUND:abc")))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                        completed("REFUND:abc", "EARN", 10)));

        recovery.recoverOne(operation, false);

        verify(operationService, never()).queryWallet("op-1");
        verify(wishFeignClient, never()).earnStarlightIdempotent(anyLong(), anyInt(), any(), anyString());
        verify(wishFeignClient, never()).spendStarlightIdempotent(anyLong(), anyInt(), any(), anyString());
        verify(operationStore).markRetry(eq(operation), eq("COMPENSATED"), any(), any(), any());
    }

    @Test
    @DisplayName("TX-04：补偿前 CAS 未命中（他人已推进）→ 不发起退款")
    void compensate_casMiss_skipsRefund() {
        PetOperation operation = operation("SPEND", "UNKNOWN");
        when(operationService.queryWallet("op-1")).thenReturn(completed("op-1", "SPEND", 10));
        when(spendRecoverable.completePendingOperation(operation)).thenReturn(false);
        when(operationStore.casStatus(operation, "UNKNOWN", "COMPENSATING")).thenReturn(false);

        recovery.recoverOne(operation, false);

        verify(wishFeignClient, never()).refundStarlightIdempotent(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("TX-04：退款被明确拒绝且退款单不存在 → MANUAL_REVIEW，不无限重试")
    void refundDefinitelyRejected_manualReview() {
        PetOperation operation = operation("SPEND", "UNKNOWN");
        when(operationService.queryWallet("op-1")).thenReturn(completed("op-1", "SPEND", 10));
        when(spendRecoverable.completePendingOperation(operation)).thenReturn(false);
        when(operationStore.casStatus(operation, "UNKNOWN", "COMPENSATING")).thenReturn(true);
        when(wishFeignClient.refundStarlightIdempotent(anyLong(), anyInt(), anyString(), anyString()))
                .thenThrow(new BusinessException("WISH_OPERATION_CONFLICT", "累计退款超过原单实扣金额"));
        when(operationService.queryWallet("REFUND:abc")).thenReturn(null);

        recovery.recoverOne(operation, false);

        verify(operationStore).markRetry(eq(operation), eq("MANUAL_REVIEW"), any(), any(), any());
    }

    @Test
    @DisplayName("TX-02：恢复重试遇远程不可用 → 保持可恢复状态退避，不当业务失败")
    void retryRemote_availabilityError_schedulesRetry() {
        PetOperation operation = operation("EARN", "UNKNOWN");
        when(operationService.queryWallet("op-1")).thenReturn(null);
        when(wishFeignClient.earnStarlightIdempotent(anyLong(), anyInt(), any(), anyString()))
                .thenThrow(new BusinessException("WISH_SERVICE_UNAVAILABLE", "服务不可用"));

        recovery.recoverOne(operation, false);

        verify(operationStore, never()).markRetry(eq(operation), eq("FAILED"), any(), any(), any());
        verify(operationStore).markRetry(eq(operation), eq("UNKNOWN"), any(), any(), any());
    }

    @Test
    @DisplayName("TX-02：恢复重试遇余额不足 → 终态 FAILED，不再重试")
    void retryRemote_definiteRejection_fails() {
        PetOperation operation = operation("EARN", "UNKNOWN");
        when(operationService.queryWallet("op-1")).thenReturn(null);
        when(wishFeignClient.earnStarlightIdempotent(anyLong(), anyInt(), any(), anyString()))
                .thenThrow(new BusinessException("WISH_STARLIGHT_INSUFFICIENT", "星光余额不足"));

        recovery.recoverOne(operation, false);

        verify(operationStore).markRetry(eq(operation), eq("FAILED"), any(), any(), any());
    }
}
