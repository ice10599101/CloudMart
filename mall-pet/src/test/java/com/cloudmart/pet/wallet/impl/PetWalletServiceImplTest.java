package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletLedger;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletLedgerMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W01 钱包核心不变量测试（T04/T08/T10 的单测层断言）：
 * 余额扣减原子性、无负余额、冻结语义、重复请求返回原结果、退款累计约束。
 * 并发行锁/CAS 与 CHECK 约束需真实 MySQL（集成测试 NOT RUN，属 Q-01 门禁）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetWalletServiceImpl 钱包核心")
class PetWalletServiceImplTest {

    @Mock
    private PetWalletAccountMapper accountMapper;
    @Mock
    private PetWalletTransactionMapper transactionMapper;
    @Mock
    private PetWalletLedgerMapper ledgerMapper;

    private PetWalletServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetWalletAccount.class);
        TableInfoHelper.initTableInfo(assistant, PetWalletTransaction.class);
        TableInfoHelper.initTableInfo(assistant, PetWalletLedger.class);
    }

    @BeforeEach
    void setUp() {
        service = new PetWalletServiceImpl(accountMapper, transactionMapper, ledgerMapper);
        lenient().when(accountMapper.update(any(), any())).thenReturn(1);
        lenient().when(transactionMapper.insert(any(PetWalletTransaction.class))).thenAnswer(inv -> {
            inv.getArgument(0, PetWalletTransaction.class).setId(900L);
            return 1;
        });
        lenient().when(ledgerMapper.insert(any(PetWalletLedger.class))).thenReturn(1);
    }

    private PetWalletAccount account(long balance, long version, String status) {
        PetWalletAccount account = new PetWalletAccount();
        account.setId(1L);
        account.setUserId(1001L);
        account.setCurrency("PET_COIN");
        account.setBalance(balance);
        account.setVersion(version);
        account.setStatus(status);
        return account;
    }

    private void stubAccount(PetWalletAccount account) {
        when(accountMapper.selectOne(any())).thenReturn(account, account);
    }

    private PetWalletCommand debit(long amount) {
        return new PetWalletCommand(1001L, 5L, "SPEND", "PURCHASE", "order-1", amount,
                "pw_abc", "hash-1", null, null, null);
    }

    @Test
    @DisplayName("扣款成功：条件更新 balance=70 version+1，账本 delta=-30 且数学一致")
    void debit_success() {
        stubAccount(account(100, 3, "ACTIVE"));

        PetWalletResult result = service.debit(debit(30));

        assertThat(result.balanceAfter()).isEqualTo(70);
        assertThat(result.duplicate()).isFalse();
        ArgumentCaptor<PetWalletAccount> accountCaptor = ArgumentCaptor.forClass(PetWalletAccount.class);
        verify(accountMapper).update(any(), any());
        ArgumentCaptor<PetWalletLedger> ledgerCaptor = ArgumentCaptor.forClass(PetWalletLedger.class);
        verify(ledgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getDelta()).isEqualTo(-30);
        assertThat(ledgerCaptor.getValue().getBalanceBefore()).isEqualTo(100);
        assertThat(ledgerCaptor.getValue().getBalanceAfter()).isEqualTo(70);
        assertThat(ledgerCaptor.getValue().getAccountVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("余额不足：PET_WALLET_INSUFFICIENT，不写流水不改余额")
    void debit_insufficient() {
        stubAccount(account(10, 1, "ACTIVE"));

        assertThatThrownBy(() -> service.debit(debit(30)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_WALLET_INSUFFICIENT));
        verify(transactionMapper, never()).insert(any(PetWalletTransaction.class));
        verify(ledgerMapper, never()).insert(any(PetWalletLedger.class));
    }

    @Test
    @DisplayName("冻结账户：SPEND/EARN 拒绝（PET_WALLET_FROZEN），REFUND 允许")
    void frozen_account_semantics() {
        stubAccount(account(100, 1, "FROZEN"));

        assertThatThrownBy(() -> service.debit(debit(10)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_WALLET_FROZEN));

        PetWalletCommand earn = new PetWalletCommand(1001L, 5L, "EARN", "QUEST_REWARD", "q-1", 5,
                "pw_x", "h", null, null, null);
        assertThatThrownBy(() -> service.credit(earn))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_WALLET_FROZEN));

        PetWalletCommand refund = new PetWalletCommand(1001L, 5L, "REFUND", "REFUND", "900:FULL", 10,
                "pw_r", "h", 900L, null, null);
        PetWalletResult result = service.credit(refund);
        assertThat(result.balanceAfter()).isEqualTo(110);
    }

    @Test
    @DisplayName("版本 CAS 0 行：PET_STATE_CONFLICT，不产生流水")
    void debit_versionConflict() {
        stubAccount(account(100, 3, "ACTIVE"));
        when(accountMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.debit(debit(10)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_STATE_CONFLICT));
        verify(transactionMapper, never()).insert(any(PetWalletTransaction.class));
    }

    @Test
    @DisplayName("P01 重复请求：锁内预检命中即重放原结果，余额 CAS 与流水插入均不发生")
    void duplicate_returnsOriginalResult() {
        stubAccount(account(100, 3, "ACTIVE"));
        PetWalletTransaction existing = new PetWalletTransaction();
        existing.setId(900L);
        existing.setOperationId("pw_abc");
        existing.setUserId(1001L);
        existing.setCurrency("PET_COIN");
        existing.setDirection("SPEND");
        existing.setBizType("PURCHASE");
        existing.setBizKey("order-1");
        existing.setAmount(30L);
        existing.setStatus("COMMITTED");
        existing.setRequestHash("hash-1");
        when(transactionMapper.selectOne(any())).thenReturn(existing);
        PetWalletLedger ledger = new PetWalletLedger();
        ledger.setBalanceAfter(70L);
        when(ledgerMapper.selectOne(any())).thenReturn(ledger);

        PetWalletResult result = service.debit(debit(30));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.balanceAfter()).isEqualTo(70);
        assertThat(result.transactionId()).isEqualTo(900L);
        verify(accountMapper, never()).update(any(), any());
        verify(transactionMapper, never()).insert(any(PetWalletTransaction.class));
        verify(ledgerMapper, never()).insert(any(PetWalletLedger.class));
    }

    @Test
    @DisplayName("P01 同 operationId 异额：PET_OPERATION_CONFLICT，且不触碰余额")
    void duplicate_sameOperationIdDifferentAmount_conflict() {
        stubAccount(account(100, 3, "ACTIVE"));
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction(30L, "hash-1"));

        assertThatThrownBy(() -> service.debit(debit(10)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_OPERATION_CONFLICT));
        verify(accountMapper, never()).update(any(), any());
        verify(transactionMapper, never()).insert(any(PetWalletTransaction.class));
    }

    @Test
    @DisplayName("P01 同事实键换 operationId 重放：返回原结果")
    void duplicate_sameFactDifferentOperationId_replays() {
        stubAccount(account(100, 3, "ACTIVE"));
        PetWalletTransaction existing = existingTransaction(30L, "hash-1");
        existing.setOperationId("pw_other");
        // 第一次按 operationId 查未命中，第二次按事实键命中
        when(transactionMapper.selectOne(any())).thenReturn(null, existing);
        PetWalletLedger ledger = new PetWalletLedger();
        ledger.setBalanceAfter(70L);
        when(ledgerMapper.selectOne(any())).thenReturn(ledger);

        PetWalletResult result = service.debit(debit(30));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.transactionId()).isEqualTo(900L);
        verify(accountMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("P01 operationId 属于其他用户：PET_OPERATION_CONFLICT")
    void duplicate_operationIdOwnedByOtherUser_conflict() {
        stubAccount(account(100, 3, "ACTIVE"));
        PetWalletTransaction existing = existingTransaction(30L, "hash-1");
        existing.setUserId(2002L);
        when(transactionMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.debit(debit(30)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_OPERATION_CONFLICT));
    }

    @Test
    @DisplayName("P01 冻结账户重放旧请求：先重放返回原结果，不抛 PET_WALLET_FROZEN")
    void duplicate_onFrozenAccount_replaysOriginalResult() {
        stubAccount(account(100, 3, "FROZEN"));
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction(30L, "hash-1"));
        PetWalletLedger ledger = new PetWalletLedger();
        ledger.setBalanceAfter(70L);
        when(ledgerMapper.selectOne(any())).thenReturn(ledger);

        PetWalletResult result = service.debit(debit(30));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.balanceAfter()).isEqualTo(70);
    }

    @Test
    @DisplayName("P01 余额不足后重放原请求：返回原结果而非 PET_WALLET_INSUFFICIENT")
    void duplicate_afterInsufficientBalance_replaysOriginalResult() {
        stubAccount(account(5, 9, "ACTIVE"));
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction(30L, "hash-1"));
        PetWalletLedger ledger = new PetWalletLedger();
        ledger.setBalanceAfter(70L);
        when(ledgerMapper.selectOne(any())).thenReturn(ledger);

        PetWalletResult result = service.debit(debit(30));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.balanceAfter()).isEqualTo(70);
    }

    @Test
    @DisplayName("P01 resolveDuplicate：已提交事实重放原结果；未命中抛 PET_REQUEST_IN_PROGRESS")
    void resolveDuplicate_semantics() {
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction(30L, "hash-1"));
        PetWalletLedger ledger = new PetWalletLedger();
        ledger.setBalanceAfter(70L);
        when(ledgerMapper.selectOne(any())).thenReturn(ledger);
        PetWalletResult replayed = service.resolveDuplicate(debit(30));
        assertThat(replayed.duplicate()).isTrue();
        assertThat(replayed.transactionId()).isEqualTo(900L);

        when(transactionMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.resolveDuplicate(debit(30)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_REQUEST_IN_PROGRESS));
    }

    private PetWalletTransaction existingTransaction(long amount, String requestHash) {
        PetWalletTransaction existing = new PetWalletTransaction();
        existing.setId(900L);
        existing.setOperationId("pw_abc");
        existing.setUserId(1001L);
        existing.setCurrency("PET_COIN");
        existing.setDirection("SPEND");
        existing.setBizType("PURCHASE");
        existing.setBizKey("order-1");
        existing.setAmount(amount);
        existing.setStatus("COMMITTED");
        existing.setRequestHash(requestHash);
        return existing;
    }

    @Test
    @DisplayName("退款：原单非 SPEND 拒绝；累计超实扣拒绝；成功走 REFUND 关联原单")
    void refundFull_semantics() {
        PetWalletTransaction earnOriginal = new PetWalletTransaction();
        earnOriginal.setId(800L);
        earnOriginal.setUserId(1001L);
        earnOriginal.setDirection("EARN");
        earnOriginal.setAmount(50L);
        earnOriginal.setStatus("COMMITTED");
        when(transactionMapper.selectByIdForUpdate(800L)).thenReturn(earnOriginal);
        assertThatThrownBy(() -> service.refundFull(800L, "pw_r", "客服退款"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_WALLET_REFUND_INVALID));

        PetWalletTransaction spendOriginal = new PetWalletTransaction();
        spendOriginal.setId(900L);
        spendOriginal.setUserId(1001L);
        spendOriginal.setDirection("SPEND");
        spendOriginal.setAmount(50L);
        spendOriginal.setStatus("COMMITTED");
        spendOriginal.setRequestHash("h");
        when(transactionMapper.selectByIdForUpdate(900L)).thenReturn(spendOriginal);
        PetWalletTransaction priorRefund = new PetWalletTransaction();
        priorRefund.setAmount(50L);
        when(transactionMapper.selectList(any())).thenReturn(List.of(priorRefund));
        assertThatThrownBy(() -> service.refundFull(900L, "pw_r2", "客服退款"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_WALLET_REFUND_INVALID));

        when(transactionMapper.selectList(any())).thenReturn(List.of());
        stubAccount(account(10, 2, "ACTIVE"));
        PetWalletResult result = service.refundFull(900L, "pw_r3", "客服退款");
        assertThat(result.balanceAfter()).isEqualTo(60);
        ArgumentCaptor<PetWalletTransaction> txCaptor = ArgumentCaptor.forClass(PetWalletTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getDirection()).isEqualTo("REFUND");
        assertThat(txCaptor.getValue().getOriginalTransactionId()).isEqualTo(900L);
        assertThat(txCaptor.getValue().getBizKey()).isEqualTo("900:FULL");
    }
}
