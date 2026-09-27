package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletAdjustment;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletAdjustmentMapper;
import com.cloudmart.pet.wallet.PetWalletService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W04 调账服务测试（§4.1 审批语义）：申请/审批人分离、重复审批幂等、
 * 并发双审单胜、负向调账走扣款、冻结账户补发允许。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetWalletAdjustmentServiceImpl 调账审批")
class PetWalletAdjustmentServiceImplTest {

    private static final Long APPLICANT = 100L;
    private static final Long APPROVER = 200L;
    private static final Long TARGET_USER = 1001L;

    @Mock
    private PetWalletAdjustmentMapper adjustmentMapper;
    @Mock
    private PetWalletAccountMapper accountMapper;
    @Mock
    private PetWalletService walletService;

    private PetWalletAdjustmentServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetWalletAdjustment.class);
        TableInfoHelper.initTableInfo(assistant, PetWalletAccount.class);
    }

    @BeforeEach
    void setUp() {
        service = new PetWalletAdjustmentServiceImpl(adjustmentMapper, accountMapper, walletService);
        lenient().when(accountMapper.update(any(), any())).thenReturn(1);
    }

    private PetWalletAdjustment pending(long delta) {
        PetWalletAdjustment adjustment = new PetWalletAdjustment();
        adjustment.setId(500L);
        adjustment.setUserId(TARGET_USER);
        adjustment.setDelta(delta);
        adjustment.setReason("客诉补偿");
        adjustment.setRequestedBy(APPLICANT);
        adjustment.setStatus("PENDING");
        adjustment.setVersion(0L);
        when(adjustmentMapper.selectById(500L)).thenReturn(adjustment);
        return adjustment;
    }

    @Test
    @DisplayName("delta=0 或原因缺失：拒绝创建")
    void apply_validation() {
        assertThatThrownBy(() -> service.apply(TARGET_USER, 0, "原因", null, APPLICANT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.apply(TARGET_USER, 100, " ", null, APPLICANT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("申请人本人审批：拒绝（§4.1 申请人与审批人不同）")
    void approve_selfRejected() {
        pending(100);
        assertThatThrownBy(() -> service.approve(500L, APPLICANT, "ok"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_VALIDATION_ERROR));
        verify(walletService, never()).credit(any(PetWalletCommand.class));
    }

    @Test
    @DisplayName("审批通过（正向）：ADJUSTMENT credit 入账并回填流水 ID")
    void approve_positive_credits() {
        pending(100);
        when(adjustmentMapper.update(any(), any())).thenReturn(1);
        when(walletService.credit(any(PetWalletCommand.class))).thenReturn(
                new PetWalletResult(950L, "pw_adj_500", 200, 100, "COMMITTED", false));

        PetWalletAdjustment result = service.approve(500L, APPROVER, "ok");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getTransactionId()).isEqualTo(950L);
        ArgumentCaptor<PetWalletCommand> captor = ArgumentCaptor.forClass(PetWalletCommand.class);
        verify(walletService).credit(captor.capture());
        assertThat(captor.getValue().bizType()).isEqualTo("ADJUSTMENT");
        assertThat(captor.getValue().bizKey()).isEqualTo("500");
        assertThat(captor.getValue().amount()).isEqualTo(100);
    }

    @Test
    @DisplayName("审批通过（负向扣回）：走 SPEND 语义 debit，金额取绝对值")
    void approve_negative_debits() {
        pending(-50);
        when(adjustmentMapper.update(any(), any())).thenReturn(1);
        when(walletService.debit(any(PetWalletCommand.class))).thenReturn(
                new PetWalletResult(951L, "pw_adj_500", 10, 50, "COMMITTED", false));

        service.approve(500L, APPROVER, "扣回误发");

        ArgumentCaptor<PetWalletCommand> captor = ArgumentCaptor.forClass(PetWalletCommand.class);
        verify(walletService).debit(captor.capture());
        assertThat(captor.getValue().direction()).isEqualTo("SPEND");
        assertThat(captor.getValue().amount()).isEqualTo(50);
        verify(walletService, never()).credit(any(PetWalletCommand.class));
    }

    @Test
    @DisplayName("重复审批：返回原状态，不产生第二次资金变动")
    void approve_duplicate_idempotent() {
        PetWalletAdjustment settled = pending(100);
        settled.setStatus("APPROVED");
        settled.setApprovedBy(APPROVER);

        PetWalletAdjustment result = service.approve(500L, APPROVER, "again");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(walletService, never()).credit(any(PetWalletCommand.class));
    }

    @Test
    @DisplayName("并发双审：CAS 0 行时重读他人结果（单胜，不双入账）")
    void approve_race_singleWinner() {
        pending(100);
        when(adjustmentMapper.update(any(), any())).thenReturn(0);
        PetWalletAdjustment winner = new PetWalletAdjustment();
        winner.setId(500L);
        winner.setStatus("APPROVED");
        winner.setApprovedBy(APPROVER);
        when(adjustmentMapper.selectById(500L)).thenReturn(winner);

        PetWalletAdjustment result = service.approve(500L, APPROVER, "race");
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(walletService, never()).credit(any(PetWalletCommand.class));
    }

    @Test
    @DisplayName("冻结状态变更：expectedVersion CAS 0 行 → 状态冲突")
    void setAccountStatus_casMiss() {
        PetWalletAccount account = new PetWalletAccount();
        account.setId(1L);
        account.setUserId(TARGET_USER);
        account.setStatus("ACTIVE");
        account.setVersion(7L);
        when(accountMapper.selectOne(any())).thenReturn(account);
        when(accountMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.setAccountStatus(TARGET_USER, true, "风控冻结", 7L, APPROVER))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_STATE_CONFLICT));
    }
}
