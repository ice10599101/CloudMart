package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetMetrics;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.feign.WishFeignClient.PetWalletOperationVO;
import com.cloudmart.pet.util.PetJsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * P02 旧结算核心语义测试（TX-01/02/05 对应单测断言）：
 * 业务事实键与请求意图键分离、错误分类（明确拒绝 vs 结果未知）、退款键固定长度、
 * 快照损坏不冒充到账。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetOperationService 旧结算语义")
class PetOperationServiceTest {

    @Mock
    private PetOperationStore operationStore;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private PetMetrics metrics;
    @Mock
    private PetProperties petProperties;
    @Mock
    private PetProperties.FeatureSwitches featureSwitches;

    private PetOperationService service;

    @BeforeEach
    void setUp() {
        lenient().when(petProperties.getFeatureSwitches()).thenReturn(featureSwitches);
        lenient().when(featureSwitches.isWalletIdempotent()).thenReturn(true);
        service = new PetOperationService(operationStore, wishFeignClient, metrics, petProperties);
    }

    @AfterEach
    void clearContext() {
        PetRequestContext.clear();
    }

    // ---------------- TX-01 键分离 ----------------

    @Test
    @DisplayName("EARN 业务事实键不掺客户端键：换请求键收敛到同一操作")
    void operationKey_ignoresClientKey() {
        PetRequestContext.setIdempotencyKey("client-key-a");
        String withKey = service.operationKey("QUEST_CLAIM", 1001L);
        PetRequestContext.setIdempotencyKey("client-key-b");
        String withOtherKey = service.operationKey("QUEST_CLAIM", 1001L);
        PetRequestContext.clear();
        String withoutKey = service.operationKey("QUEST_CLAIM", 1001L);

        assertThat(withKey).isEqualTo(withOtherKey).isEqualTo(withoutKey).isEqualTo("QUEST_CLAIM:1001");
    }

    @Test
    @DisplayName("SPEND 请求意图键携带客户端键：同键收敛、换键独立")
    void requestOperationKey_includesClientKey() {
        PetRequestContext.setIdempotencyKey("intent-1");
        String first = service.requestOperationKey("SHOP_BUY", 1L, 2L, "EQUIPMENT", "sword");
        String retry = service.requestOperationKey("SHOP_BUY", 1L, 2L, "EQUIPMENT", "sword");
        assertThat(first).isEqualTo(retry).isEqualTo("SHOP_BUY:1:2:EQUIPMENT:sword:intent-1");

        PetRequestContext.setIdempotencyKey("intent-2");
        String second = service.requestOperationKey("SHOP_BUY", 1L, 2L, "EQUIPMENT", "sword");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("SPEND 缺客户端键回退每请求一个服务端键（重复购买不再被去重成免费发放）")
    void requestOperationKey_fallsBackToPerRequestKey() {
        String first = service.requestOperationKey("SHOP_BUY", 1L, 2L, "EQUIPMENT", "sword");
        String second = service.requestOperationKey("SHOP_BUY", 1L, 2L, "EQUIPMENT", "sword");
        assertThat(first).isNotEqualTo(second);
        assertThat(first).startsWith("SHOP_BUY:1:2:EQUIPMENT:sword:");
    }

    // ---------------- TX-05 键长度 ----------------

    @Test
    @DisplayName("超长键折叠为固定摘要且确定性：重试得到同一折叠键")
    void key_foldsDeterministically_overMaxLength() {
        StringBuilder longId = new StringBuilder("x");
        for (int i = 0; i < 200; i++) {
            longId.append("y");
        }
        String first = service.operationKey("BATTLE_REWARD", longId.toString(), "attacker");
        String retry = service.operationKey("BATTLE_REWARD", longId.toString(), "attacker");
        assertThat(first).isEqualTo(retry);
        assertThat(first.length()).isLessThanOrEqualTo(160);
        assertThat(first).startsWith("BATTLE_REWARD:");
    }

    @Test
    @DisplayName("退款键为固定长度摘要派生：不受原单键长影响、可复现")
    void refundOperationId_fixedLengthDigest() {
        String refund = service.refundOperationId("x".repeat(300));
        assertThat(refund).startsWith("REFUND:");
        assertThat(refund.length()).isLessThanOrEqualTo(72);
        assertThat(refund).isEqualTo(service.refundOperationId("x".repeat(300)));
        assertThat(refund).isNotEqualTo(service.refundOperationId("other-original"));
    }

    // ---------------- TX-02 错误分类 ----------------

    @Test
    @DisplayName("isDefiniteRejection：余额不足/键冲突为明确拒绝；服务不可用不是")
    void definiteRejection_classification() {
        assertThat(PetOperationService.isDefiniteRejection(
                new BusinessException("WISH_STARLIGHT_INSUFFICIENT", "星光余额不足"))).isTrue();
        assertThat(PetOperationService.isDefiniteRejection(
                new BusinessException("WISH_OPERATION_CONFLICT", "操作键冲突"))).isTrue();
        assertThat(PetOperationService.isDefiniteRejection(
                new BusinessException("WISH_SERVICE_UNAVAILABLE", "服务不可用"))).isFalse();
        assertThat(PetOperationService.isDefiniteRejection(
                new BusinessException("PET_VALIDATION_ERROR", "参数错误"))).isFalse();
    }

    private PetOperation pendingOperation() {
        PetOperation operation = new PetOperation();
        operation.setId(1L);
        operation.setOperationId("QUEST_CLAIM:1001");
        operation.setUserId(1001L);
        operation.setBizType("QUEST_CLAIM");
        operation.setDirection("EARN");
        operation.setAmount(10);
        operation.setStatus("PENDING");
        return operation;
    }

    @Test
    @DisplayName("远程不可用（降级 BusinessException）标 UNKNOWN 返回结算中，不再当业务失败")
    void execute_availabilityError_marksUnknown() {
        PetOperation operation = pendingOperation();
        when(operationStore.claim(anyString(), anyLong(), any(), anyString(), any(), anyString(), anyInt(), any()))
                .thenReturn(operation);
        when(wishFeignClient.earnStarlightIdempotent(anyLong(), anyInt(), any(), anyString()))
                .thenThrow(new BusinessException("WISH_SERVICE_UNAVAILABLE", "服务不可用"));

        var settlement = service.executeEarn("QUEST_CLAIM:1001", 1001L, 5L, "QUEST_CLAIM", 9L, 10, null);

        assertThat(settlement.isUnknown()).isTrue();
        verify(operationStore).markTerminal(eq(operation), eq("UNKNOWN"), anyString(), any());
        verify(operationStore, never()).markTerminal(eq(operation), eq("FAILED"), any(), any());
    }

    @Test
    @DisplayName("余额不足（明确拒绝）标 FAILED 并原样抛出")
    void execute_insufficientBalance_marksFailedAndRethrows() {
        PetOperation operation = pendingOperation();
        operation.setDirection("SPEND");
        when(operationStore.claim(anyString(), anyLong(), any(), anyString(), any(), anyString(), anyInt(), any()))
                .thenReturn(operation);
        when(wishFeignClient.spendStarlightIdempotent(anyLong(), anyInt(), any(), anyString()))
                .thenThrow(new BusinessException("WISH_STARLIGHT_INSUFFICIENT", "星光余额不足"));

        assertThatThrownBy(() -> service.executeSpend("SHOP_BUY:1", 1001L, 5L, "SHOP_BUY", 5L, 10, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额不足");
        verify(operationStore).markTerminal(eq(operation), eq("FAILED"), anyString(), any());
    }

    @Test
    @DisplayName("信封成功但交易状态非 COMPLETED：按未知处理，不冒充成功")
    void execute_nonCompletedStatus_marksUnknown() {
        PetOperation operation = pendingOperation();
        when(operationStore.claim(anyString(), anyLong(), any(), anyString(), any(), anyString(), anyInt(), any()))
                .thenReturn(operation);
        when(wishFeignClient.earnStarlightIdempotent(anyLong(), anyInt(), any(), anyString()))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(new PetWalletOperationVO(
                        "op-1", "EARN", 10, 10, 100, "PET_REWARD", 9L, "PENDING", false)));

        var settlement = service.executeEarn("QUEST_CLAIM:1001", 1001L, 5L, "QUEST_CLAIM", 9L, 10, null);

        assertThat(settlement.isUnknown()).isTrue();
        verify(operationStore).markTerminal(eq(operation), eq("UNKNOWN"), anyString(), any());
    }

    // ---------------- 快照损坏（P02） ----------------

    @Test
    @DisplayName("已完成操作的快照损坏：转 MANUAL_REVIEW 并按处理中拒绝，禁止拿金额冒充到账")
    void execute_corruptSnapshot_transfersToManualReview() {
        PetOperation operation = pendingOperation();
        operation.setStatus("COMPLETED");
        operation.setWalletResult("not-a-json");
        operation.setAmount(10);
        when(operationStore.claim(anyString(), anyLong(), any(), anyString(), any(), anyString(), anyInt(), any()))
                .thenReturn(operation);

        assertThatThrownBy(() -> service.executeEarn("QUEST_CLAIM:1001", 1001L, 5L, "QUEST_CLAIM", 9L, 10, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_SETTLEMENT_PENDING);
        verify(operationStore).markManualReview(eq(operation), anyString());
    }

    @Test
    @DisplayName("已完成操作的合法快照：返回原结果（duplicate）")
    void execute_completedOperation_returnsOriginalResult() {
        PetOperation operation = pendingOperation();
        operation.setStatus("COMPLETED");
        PetWalletOperationVO vo = new PetWalletOperationVO("op-1", "EARN", 10, 8, 100, "PET_REWARD", 9L,
                "COMPLETED", true);
        operation.setWalletResult(PetJsonUtils.toJson(vo));
        when(operationStore.claim(anyString(), anyLong(), any(), anyString(), any(), anyString(), anyInt(), any()))
                .thenReturn(operation);

        var settlement = service.executeEarn("QUEST_CLAIM:1001", 1001L, 5L, "QUEST_CLAIM", 9L, 10, null);

        assertThat(settlement.isCompleted()).isTrue();
        assertThat(settlement.credited()).isEqualTo(8);
        assertThat(settlement.duplicate()).isTrue();
    }
}
