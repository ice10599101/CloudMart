package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.entity.PetAssetGrant;
import com.cloudmart.pet.entity.PetPurchaseOrder;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.repository.PetAssetGrantMapper;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.PetWalletService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P02/QA35 恢复扫描器测试（单测层）：租约到期接管后必须按本地业务事实收敛——
 * 订单 COMPLETED 按事实完成幂等键（不重扣不重发）；无已提交事实置 FAILED 释放同键重试。
 * 事务边界行为由真实 MySQL 集成测试（PetPurchaseRecoveryIntegrationTest）证明。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P02 购买幂等恢复扫描器")
class PetPurchaseRecoveryServiceTest {

    @Mock
    private PetRequestDedupMapper dedupMapper;
    @Mock
    private PetPurchaseOrderMapper orderMapper;
    @Mock
    private PetAssetGrantMapper assetGrantMapper;
    @Mock
    private PetWalletService walletService;

    private RecordingDedupService dedupService;
    private PetPurchaseRecoveryService recoveryService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetRequestDedup.class);
        TableInfoHelper.initTableInfo(assistant, PetPurchaseOrder.class);
        TableInfoHelper.initTableInfo(assistant, PetAssetGrant.class);
        TableInfoHelper.initTableInfo(assistant, PetWalletAccount.class);
    }

    @BeforeEach
    void setUp() {
        dedupService = new RecordingDedupService(dedupMapper);
        PetPurchaseApplicationService purchaseService = new PetPurchaseApplicationService(
                dedupService, walletService, null, staticProvider(null), staticProvider(null),
                orderMapper, assetGrantMapper, null);
        recoveryService = new PetPurchaseRecoveryService(dedupService, dedupMapper, orderMapper, purchaseService);
        lenient().when(walletService.getOrCreateAccount(anyLong())).thenAnswer(inv -> {
            PetWalletAccount account = new PetWalletAccount();
            account.setBalance(88L);
            return account;
        });
    }

    @Test
    @DisplayName("订单 COMPLETED：按业务事实完成幂等键（终态含原订单 ID），不 markFailed")
    void recover_completedOrder_finalizesDedup() {
        PetRequestDedup row = expiredRow();
        when(dedupMapper.selectList(any())).thenReturn(List.of(row));
        when(dedupMapper.update(any(), any())).thenReturn(1);
        PetPurchaseOrder order = new PetPurchaseOrder();
        order.setId(700L);
        order.setUserId(1001L);
        order.setRequestKey("intent-key-000001");
        order.setItemType("FOOD");
        order.setItemCode("cake");
        order.setStatus("COMPLETED");
        order.setWalletTransactionId(900L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        PetAssetGrant grant = new PetAssetGrant();
        grant.setRewardSlot("cake");
        when(assetGrantMapper.selectList(any())).thenReturn(List.of(grant));

        int recovered = recoveryService.recoverExpiredLeases();

        assertThat(recovered).isEqualTo(1);
        assertThat(dedupService.completed).hasSize(1);
        assertThat(dedupService.completed.get(0).bizOrderId).isEqualTo(700L);
        assertThat(dedupService.completed.get(0).responseJson)
                .contains("\"orderId\":\"700\"").contains("cake");
        assertThat(dedupService.failed).isEmpty();
    }

    @Test
    @DisplayName("无已提交业务事实：置 FAILED 释放同键重试（不得虚构成功结果）")
    void recover_noCommittedFact_marksRetryable() {
        PetRequestDedup row = expiredRow();
        when(dedupMapper.selectList(any())).thenReturn(List.of(row));
        when(dedupMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.selectOne(any())).thenReturn(null);

        int recovered = recoveryService.recoverExpiredLeases();

        assertThat(recovered).isEqualTo(1);
        assertThat(dedupService.failed).hasSize(1);
        assertThat(dedupService.failed.get(0).requestKey).isEqualTo("intent-key-000001");
        assertThat(dedupService.completed).isEmpty();
    }

    @Test
    @DisplayName("接管 CAS 失败（并发被抢）：跳过该行，不推进任何终态")
    void recover_takeoverLost_skips() {
        PetRequestDedup row = expiredRow();
        when(dedupMapper.selectList(any())).thenReturn(List.of(row));
        when(dedupMapper.update(any(), any())).thenReturn(0);

        int recovered = recoveryService.recoverExpiredLeases();

        assertThat(recovered).isZero();
        assertThat(dedupService.completed).isEmpty();
        assertThat(dedupService.failed).isEmpty();
        verify(orderMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("租约未到期的行不在扫描结果中（查询条件过滤），不处理")
    void recover_onlyExpiredLeasesSelected() {
        when(dedupMapper.selectList(any())).thenReturn(List.of());

        assertThat(recoveryService.recoverExpiredLeases()).isZero();
        verify(dedupMapper, never()).update(any(), any());
    }

    private PetRequestDedup expiredRow() {
        PetRequestDedup row = new PetRequestDedup();
        row.setId(1L);
        row.setUserId(1001L);
        row.setEndpointKey("PURCHASE");
        row.setRequestKey("intent-key-000001");
        row.setStatus("PROCESSING");
        row.setLeaseUntil(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(5));
        return row;
    }

    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> staticProvider(T value) {
        org.springframework.beans.factory.ObjectProvider<T> provider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        lenient().when(provider.getIfAvailable()).thenReturn(value);
        lenient().when(provider.iterator()).thenReturn(java.util.Collections.<T>emptyListIterator());
        lenient().when(provider.stream()).thenReturn(java.util.stream.Stream.empty());
        return provider;
    }

    /** 记录终态调用的 dedup 桩（接管 CAS 走 mock 的 update 计数） */
    private static class RecordingDedupService extends PetRequestDedupServiceImpl {
        private record Completion(Long bizOrderId, String responseJson) {
        }

        private record Failure(String requestKey, String errorJson) {
        }

        private final List<Completion> completed = new java.util.ArrayList<>();
        private final List<Failure> failed = new java.util.ArrayList<>();

        private RecordingDedupService(PetRequestDedupMapper dedupMapper) {
            super(dedupMapper);
        }

        @Override
        public void completeSucceeded(Long userId, String endpointKey, String requestKey,
                                      String expectedLeaseOwner, Long bizOrderId, String responseJson) {
            completed.add(new Completion(bizOrderId, responseJson));
        }

        @Override
        public void markFailed(Long userId, String endpointKey, String requestKey,
                               String expectedLeaseOwner, String errorJson) {
            failed.add(new Failure(requestKey, errorJson));
        }
    }
}
