package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.PetRequestDedupService.ClaimResult;
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
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * W01 请求幂等测试（T05/T07 单测层断言）：同键不同内容 409、同键返回原终态、
 * FAILED 可重试且并发只有一个执行者。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetRequestDedupServiceImpl 请求幂等")
class PetRequestDedupServiceImplTest {

    private static final String HASH = "a".repeat(64);

    @Mock
    private PetRequestDedupMapper dedupMapper;

    private PetRequestDedupServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PetRequestDedup.class);
    }

    @BeforeEach
    void setUp() {
        service = new PetRequestDedupServiceImpl(dedupMapper);
    }

    private PetRequestDedup existing(String status, String payloadHash, String responseJson) {
        PetRequestDedup row = new PetRequestDedup();
        row.setId(1L);
        row.setUserId(1001L);
        row.setEndpointKey("PURCHASE");
        row.setRequestKey("intent-key-000001");
        row.setPayloadHash(payloadHash);
        row.setStatus(status);
        row.setResponseJson(responseJson);
        return row;
    }

    @Test
    @DisplayName("首次占键：NEW")
    void claim_new() {
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenReturn(1);

        assertThat(service.claim(1001L, "PURCHASE", "intent-key-000001", HASH).outcome())
                .isEqualTo(ClaimResult.Outcome.NEW);
    }

    @Test
    @DisplayName("同键同内容且已完成：EXISTING + 原响应")
    void claim_existingCompleted() {
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenThrow(new DuplicateKeyException("uk"));
        when(dedupMapper.selectOne(any()))
                .thenReturn(existing("COMPLETED", HASH, "{\"orderId\":\"1\"}"));

        ClaimResult result = service.claim(1001L, "PURCHASE", "intent-key-000001", HASH);
        assertThat(result.outcome()).isEqualTo(ClaimResult.Outcome.EXISTING);
        assertThat(result.responseJson()).contains("orderId");
    }

    @Test
    @DisplayName("同键不同内容：409 PET_IDEMPOTENCY_CONFLICT，禁止自动换键")
    void claim_conflictOnDifferentPayload() {
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenThrow(new DuplicateKeyException("uk"));
        when(dedupMapper.selectOne(any()))
                .thenReturn(existing("COMPLETED", "b".repeat(64), null));

        assertThatThrownBy(() -> service.claim(1001L, "PURCHASE", "intent-key-000001", HASH))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_IDEMPOTENCY_CONFLICT));
    }

    @Test
    @DisplayName("同键处理中：IN_PROGRESS（202 语义）")
    void claim_inProgress() {
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenThrow(new DuplicateKeyException("uk"));
        when(dedupMapper.selectOne(any())).thenReturn(existing("PROCESSING", HASH, null));

        assertThat(service.claim(1001L, "PURCHASE", "intent-key-000001", HASH).outcome())
                .isEqualTo(ClaimResult.Outcome.IN_PROGRESS);
    }

    @Test
    @DisplayName("FAILED 行同键重试：CAS 抢占成功回 NEW；被并发抢占回 IN_PROGRESS")
    void claim_failedRetry() {
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenThrow(new DuplicateKeyException("uk"));
        when(dedupMapper.selectOne(any())).thenReturn(existing("FAILED", HASH, null));
        when(dedupMapper.update(any(), any())).thenReturn(1, 0);

        assertThat(service.claim(1001L, "PURCHASE", "intent-key-000001", HASH).outcome())
                .isEqualTo(ClaimResult.Outcome.NEW);
        assertThat(service.claim(1001L, "PURCHASE", "intent-key-000001", HASH).outcome())
                .isEqualTo(ClaimResult.Outcome.IN_PROGRESS);
    }

    @Test
    @DisplayName("请求键契约：16..128 ASCII")
    void requestKey_validation() {
        assertThat(PetRequestDedupService.isValidRequestKey(null)).isFalse();
        assertThat(PetRequestDedupService.isValidRequestKey("short")).isFalse();
        assertThat(PetRequestDedupService.isValidRequestKey("k".repeat(129))).isFalse();
        assertThat(PetRequestDedupService.isValidRequestKey("包含中文的键xxxxxxxxxx")).isFalse();
        assertThat(PetRequestDedupService.isValidRequestKey("k".repeat(16))).isTrue();
        assertThat(PetRequestDedupService.isValidRequestKey("k".repeat(128))).isTrue();
    }
}
