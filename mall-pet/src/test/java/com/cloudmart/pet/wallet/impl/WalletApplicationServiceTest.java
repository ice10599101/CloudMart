package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetAssetGrant;
import com.cloudmart.pet.entity.PetPurchaseOrder;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.entity.PetRewardClaim;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.repository.PetAssetGrantMapper;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.repository.PetRewardClaimMapper;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import com.cloudmart.pet.wallet.PetPurchaseCatalog.CatalogEntry;
import com.cloudmart.pet.wallet.PetRewardApplicationService.RewardClaimCommand;
import com.cloudmart.pet.wallet.PetRewardApplicationService.RewardClaimResult;
import com.cloudmart.pet.wallet.PetRewardApplicationService.ResolvedReward;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W01 应用服务测试（T06/T08/T09 单测层断言）：
 * 不可重复物品零扣款拒绝、重复领取返回原结果且不重算、余额不足不产生领取事实。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("W01 购买与领奖应用服务")
class WalletApplicationServiceTest {

    @Mock
    private PetRequestDedupMapper dedupMapper;
    @Mock
    private PetWalletService walletService;
    @Mock
    private PetPurchaseCatalog catalog;
    @Mock
    private PetPurchaseOrderMapper orderMapper;
    @Mock
    private PetAssetGrantMapper assetGrantMapper;
    @Mock
    private PetRewardClaimMapper claimMapper;

    private TransactionTemplate transactionTemplate;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetRequestDedup.class);
        TableInfoHelper.initTableInfo(assistant, PetPurchaseOrder.class);
        TableInfoHelper.initTableInfo(assistant, PetRewardClaim.class);
        TableInfoHelper.initTableInfo(assistant, PetAssetGrant.class);
        TableInfoHelper.initTableInfo(assistant, PetWalletAccount.class);
    }

    private TransactionTemplate txTemplate() {
        if (transactionTemplate == null) {
            PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
            lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            transactionTemplate = new TransactionTemplate(tm);
        }
        return transactionTemplate;
    }

    private PetRequestDedupServiceStub dedup() {
        return new PetRequestDedupServiceStub(dedupMapper);
    }

    // ---------------- 购买 ----------------

    @Test
    @DisplayName("T06：不可重复物品已拥有 → ALREADY_OWNED 零扣款 + 保存终态拒绝响应")
    void purchase_alreadyOwned_zeroCharge() {
        PetRequestDedupServiceStub dedup = dedup();
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenReturn(1);
        PetPurchaseApplicationService service = new PetPurchaseApplicationService(dedup, walletService,
                catalogProvider(catalog), emptyProvider(), orderMapper, assetGrantMapper, txTemplate());
        when(catalog.load(any(), any(), any(), any(), any()))
                .thenReturn(new CatalogEntry("EQUIPMENT", "sword", 100, "v1", "剑", "r-key"));
        when(catalog.isUniquePerUser("EQUIPMENT")).thenReturn(true);
        when(catalog.isOwnedByUser(1001L, "EQUIPMENT", "sword")).thenReturn(true);

        assertThatThrownBy(() -> service.purchase(1001L, 5L, "EQUIPMENT", "sword", "intent-key-000001", "v1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_ITEM_ALREADY_OWNED));
        verify(walletService, never()).debit(any(PetWalletCommand.class));
    }

    @Test
    @DisplayName("购买成功：建订单 → 扣款 → 交付 → 订单完成；dedup 终态落库")
    void purchase_success() {
        PetRequestDedupServiceStub dedup = dedup();
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenReturn(1);
        when(dedupMapper.update(any(), any())).thenReturn(1);
        PetPurchaseApplicationService service = new PetPurchaseApplicationService(dedup, walletService,
                catalogProvider(catalog), emptyProvider(), orderMapper, assetGrantMapper, txTemplate());
        when(catalog.load(any(), any(), any(), any(), any()))
                .thenReturn(new CatalogEntry("FOOD", "cake", 20, "v1", "蛋糕", "r-key"));
        when(catalog.isUniquePerUser("FOOD")).thenReturn(false);
        when(orderMapper.insert(any(PetPurchaseOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, PetPurchaseOrder.class).setId(700L);
            return 1;
        });
        when(walletService.debit(any(PetWalletCommand.class))).thenReturn(
                new PetWalletResult(900L, "pw_op", 80, 20, "COMMITTED", false));

        var result = service.purchase(1001L, 5L, "FOOD", "cake", "intent-key-000002", "v1");

        assertThat(result.duplicate()).isFalse();
        assertThat(result.balanceAfter()).isEqualTo(80);
        assertThat(result.deliveredSlots()).containsExactly("cake");
        ArgumentCaptor<PetWalletCommand> debitCaptor = ArgumentCaptor.forClass(PetWalletCommand.class);
        verify(walletService).debit(debitCaptor.capture());
        assertThat(debitCaptor.getValue().amount()).isEqualTo(20);
        assertThat(debitCaptor.getValue().bizKey()).isEqualTo("700");
        assertThat(dedup.completed).isEqualTo(1);
    }

    @Test
    @DisplayName("T07：同请求键重试返回原终态结果，不再走目录与扣款")
    void purchase_sameKey_returnsStoredResult() {
        PetRequestDedupServiceStub dedup = dedup();
        when(dedupMapper.insert(any(PetRequestDedup.class))).thenThrow(new DuplicateKeyException("uk"));
        when(dedupMapper.selectOne(any())).thenAnswer(inv -> {
            PetRequestDedup row = new PetRequestDedup();
            row.setStatus("COMPLETED");
            row.setPayloadHash(dedup.canonicalHash(1001L, "5", "FOOD", "cake", "v1"));
            row.setResponseJson("{\"orderId\":\"700\",\"operationId\":\"pw_x\",\"walletTransactionId\":\"900\","
                    + "\"balanceAfter\":80,\"itemType\":\"FOOD\",\"itemCode\":\"cake\","
                    + "\"deliveredSlots\":[\"cake\"],\"duplicate\":false,\"errorCode\":null}");
            return row;
        });
        PetPurchaseApplicationService service = new PetPurchaseApplicationService(dedup, walletService,
                catalogProvider(catalog), emptyProvider(), orderMapper, assetGrantMapper, txTemplate());

        var result = service.purchase(1001L, 5L, "FOOD", "cake", "intent-key-000002", "v1");

        assertThat(result.duplicate()).isTrue();
        assertThat(result.orderId()).isEqualTo("700");
        verify(catalog, never()).load(any(), any(), any(), any(), any());
        verify(walletService, never()).debit(any(PetWalletCommand.class));
    }

    // ---------------- 领奖 ----------------

    private RewardClaimCommand rewardCommand() {
        return new RewardClaimCommand(1001L, 5L, "ACTIVITY_REWARD", "activity-1", "MAIN", null);
    }

    @Test
    @DisplayName("T09：首次领取 → 解析冻结一次、钱包入账、claim 完成")
    void claim_first_resolvesAndCredits() {
        PetRequestDedupServiceStub dedup = dedup();
        when(claimMapper.insert(any(PetRewardClaim.class))).thenAnswer(inv -> {
            inv.getArgument(0, PetRewardClaim.class).setId(600L);
            return 1;
        });
        when(walletService.credit(any(PetWalletCommand.class))).thenReturn(
                new PetWalletResult(901L, "pw_op", 130, 30, "COMMITTED", false));
        PetRewardApplicationServiceImpl service = new PetRewardApplicationServiceImpl(
                claimMapper, walletService, assetGrantMapper, dedup, emptyProvider(), txTemplate());
        AtomicInteger resolveCount = new AtomicInteger();

        RewardClaimResult result = service.claim(rewardCommand(), command -> {
            resolveCount.incrementAndGet();
            return new ResolvedReward(30, null, List.of("badge:cooperation_badge"), "v1");
        });

        assertThat(result.duplicate()).isFalse();
        assertThat(result.coin()).isEqualTo(30);
        assertThat(result.balanceAfter()).isEqualTo(130);
        assertThat(resolveCount.get()).isEqualTo(1);
        ArgumentCaptor<PetWalletCommand> creditCaptor = ArgumentCaptor.forClass(PetWalletCommand.class);
        verify(walletService).credit(creditCaptor.capture());
        assertThat(creditCaptor.getValue().bizKey()).isEqualTo("activity-1:MAIN");
        assertThat(creditCaptor.getValue().petId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("T09：重复领取（唯一键冲突）→ 返回原结果，不重算不重发")
    void claim_duplicate_returnsOriginal() {
        PetRequestDedupServiceStub dedup = dedup();
        when(claimMapper.insert(any(PetRewardClaim.class))).thenThrow(new DuplicateKeyException("uk"));
        when(claimMapper.selectOne(any())).thenAnswer(inv -> {
            PetRewardClaim row = new PetRewardClaim();
            row.setId(600L);
            row.setStatus("COMPLETED");
            row.setResultJson("{\"coin\":30,\"balanceAfter\":130,\"slots\":[\"cooperation_badge\"]}");
            return row;
        });
        PetRewardApplicationServiceImpl service = new PetRewardApplicationServiceImpl(
                claimMapper, walletService, assetGrantMapper, dedup, emptyProvider(), txTemplate());
        AtomicInteger resolveCount = new AtomicInteger();

        RewardClaimResult result = service.claim(rewardCommand(), command -> {
            resolveCount.incrementAndGet();
            return new ResolvedReward(999, null, List.of(), "v1");
        });

        assertThat(result.duplicate()).isTrue();
        assertThat(result.coin()).isEqualTo(30);
        assertThat(resolveCount.get()).isEqualTo(0);
        verify(walletService, never()).credit(any(PetWalletCommand.class));
    }

    @Test
    @DisplayName("零币奖励：不调用钱包入账")
    void claim_zeroCoin_skipsWallet() {
        PetRequestDedupServiceStub dedup = dedup();
        when(claimMapper.insert(any(PetRewardClaim.class))).thenAnswer(inv -> {
            inv.getArgument(0, PetRewardClaim.class).setId(600L);
            return 1;
        });
        PetWalletAccount account = new PetWalletAccount();
        account.setBalance(11L);
        when(walletService.getOrCreateAccount(1001L)).thenReturn(account);
        PetRewardApplicationServiceImpl service = new PetRewardApplicationServiceImpl(
                claimMapper, walletService, assetGrantMapper, dedup, emptyProvider(), txTemplate());

        RewardClaimResult result = service.claim(rewardCommand(),
                command -> new ResolvedReward(0, null, List.of(), "v1"));

        assertThat(result.coin()).isEqualTo(0);
        assertThat(result.balanceAfter()).isEqualTo(11);
        verify(walletService, never()).credit(any(PetWalletCommand.class));
    }

    // ---------------- 辅助 ----------------

    @SuppressWarnings("unchecked")
    private static ObjectProvider<PetPurchaseCatalog> catalogProvider(PetPurchaseCatalog catalog) {
        ObjectProvider<PetPurchaseCatalog> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.lenient().when(provider.getIfAvailable()).thenReturn(catalog);
        return provider;
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        lenient().when(provider.iterator()).thenReturn(java.util.Collections.<T>emptyListIterator());
        lenient().when(provider.stream()).thenReturn(java.util.stream.Stream.empty());
        return provider;
    }

    /** 请求幂等桩：真实占键逻辑（REQUIRES_NEW 在单测中直接执行） */
    private static class PetRequestDedupServiceStub extends PetRequestDedupServiceImpl {
        private int completed;

        private PetRequestDedupServiceStub(PetRequestDedupMapper dedupMapper) {
            super(dedupMapper);
        }

        @Override
        public void completeSucceeded(Long userId, String endpointKey, String requestKey,
                                      Long bizOrderId, String responseJson) {
            completed++;
        }
    }
}
