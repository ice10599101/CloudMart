package com.cloudmart.pet.wallet.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.wallet.PetEconomyService.Mode;
import com.cloudmart.pet.wallet.PetEconomyService.WalletSettlement;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W02 结算门面路由测试：PET 走独立钱包（事实键/操作键）、
 * PAUSED 维护拒绝、非法配置 fail-safe 回退。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetEconomyServiceImpl 模式路由")
class PetEconomyServiceImplTest {

    @Mock
    private PetProperties properties;
    @Mock
    private PetWalletService walletService;
    @Mock
    private PetRequestDedupService dedupService;

    private PetEconomyServiceImpl service;

    private PetEconomyServiceImpl serviceOf(String mode) {
        lenient().when(properties.getWalletMode()).thenReturn(mode);
        lenient().when(dedupService.canonicalHash(anyString())).thenReturn("a".repeat(64));
        lenient().when(dedupService.canonicalHash(any(), any(), any())).thenReturn("a".repeat(64));
        return new PetEconomyServiceImpl(properties, walletService, dedupService);
    }

    @AfterEach
    void clear() {
        PetRequestContext.clear();
    }

    @Test
    @DisplayName("P03 新基线：null/blank 与非法值一律 PET_COIN（唯一正账本，fail-safe）")
    void mode_routing_defaultsToPet() {
        assertThat(serviceOf(null).mode()).isEqualTo(Mode.PET);
        assertThat(serviceOf("").mode()).isEqualTo(Mode.PET);
        assertThat(serviceOf("bogus").mode()).isEqualTo(Mode.PET);
    }


    @Test
    @DisplayName("PAUSED：新收支拒绝 PET_WALLET_MAINTENANCE；余额不展示")
    void paused_rejectsNewFlows() {
        service = serviceOf("PAUSED");
        assertThatThrownBy(() -> service.earn(1001L, 5L, "QUEST_CLAIM", 9L, 10, null, 9L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_WALLET_MAINTENANCE));
        assertThatThrownBy(() -> service.spend(1001L, 5L, "SHOP_BUY", 5L, 10, null, 5L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_WALLET_MAINTENANCE));
        assertThat(service.balanceOf(1001L)).isNull();
        verify(walletService, never()).credit(any(PetWalletCommand.class));
        verify(walletService, never()).debit(any(PetWalletCommand.class));
    }

    @Test
    @DisplayName("PET：EARN 走钱包 credit，事实键=parts 连接，操作键为 pw_ 前缀")
    void pet_earn_usesWallet() {
        service = serviceOf("PET");
        when(walletService.credit(any(PetWalletCommand.class))).thenReturn(
                new PetWalletResult(901L, "pw_x", 110, 10, "COMMITTED", false));

        WalletSettlement result = service.earn(1001L, 5L, "BATTLE_REWARD", 11L, 10, null, 11L, "attacker");

        assertThat(result.isCompleted()).isTrue();
        assertThat(result.credited()).isEqualTo(10);
        ArgumentCaptor<PetWalletCommand> captor = ArgumentCaptor.forClass(PetWalletCommand.class);
        verify(walletService).credit(captor.capture());
        assertThat(captor.getValue().bizKey()).isEqualTo("11:attacker");
        assertThat(captor.getValue().operationId()).startsWith("pw_");
        assertThat(captor.getValue().direction()).isEqualTo("EARN");
        assertThat(captor.getValue().bizType()).isEqualTo("BATTLE_REWARD");
    }

    @Test
    @DisplayName("PET：SPEND 意图键含客户端请求键（新意图独立扣款，重试收敛原单）")
    void pet_spend_includesClientKey() {
        service = serviceOf("PET");
        PetRequestContext.setIdempotencyKey("intent-key-000001");
        when(walletService.debit(any(PetWalletCommand.class))).thenReturn(
                new PetWalletResult(902L, "pw_y", 90, 20, "COMMITTED", false));

        service.spend(1001L, 5L, "SHOP_BUY", 5L, 20, null, 1001L, 5L, "EQUIPMENT", "sword");

        ArgumentCaptor<PetWalletCommand> captor = ArgumentCaptor.forClass(PetWalletCommand.class);
        verify(walletService).debit(captor.capture());
        assertThat(captor.getValue().bizKey()).isEqualTo("1001:5:EQUIPMENT:sword:intent-key-000001");
        assertThat(captor.getValue().direction()).isEqualTo("SPEND");
    }

    @Test
    @DisplayName("PET：钱包重复请求返回原结果（duplicate 透传）")
    void pet_duplicate_passthrough() {
        service = serviceOf("PET");
        when(walletService.credit(any(PetWalletCommand.class))).thenReturn(
                new PetWalletResult(901L, "pw_x", 110, 10, "COMMITTED", true));

        WalletSettlement result = service.earn(1001L, 5L, "QUEST_CLAIM", 9L, 10, null, 9L);
        assertThat(result.isCompleted()).isTrue();
        assertThat(result.duplicate()).isTrue();
    }

    @Test
    @DisplayName("余额路由：PET 读钱包余额")
    void balance_routing() {
        service = serviceOf("PET");
        com.cloudmart.pet.entity.PetWalletAccount account = new com.cloudmart.pet.entity.PetWalletAccount();
        account.setBalance(77L);
        when(walletService.getOrCreateAccount(1001L)).thenReturn(account);
        assertThat(service.balanceOf(1001L)).isEqualTo(77L);

    }
}
