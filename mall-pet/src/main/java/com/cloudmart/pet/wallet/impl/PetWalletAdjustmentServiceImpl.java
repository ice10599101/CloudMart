package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletAdjustment;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletAdjustmentMapper;
import com.cloudmart.pet.wallet.PetWalletAdjustmentService;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 宠物币调账服务实现（W04）。
 *
 * <p>审批入账事务：CAS 抢占审批权（PENDING→APPROVED + approved_by + version）成功者
 * 才发起钱包 credit（MANDATORY 加入本事务）——并发双审只有一方能入账（T39 语义侧）。
 * 重复审批：CAS 0 行说明已被处理，重读记录按原状态返回。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetWalletAdjustmentServiceImpl implements PetWalletAdjustmentService {

    private final PetWalletAdjustmentMapper adjustmentMapper;
    private final PetWalletAccountMapper accountMapper;
    private final PetWalletService walletService;

    @Override
    public PetWalletAdjustment apply(Long targetUserId, long delta, String reason,
                                     String ticketNo, Long operatorAdminId) {
        validateOperator(operatorAdminId);
        if (delta == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "调账金额不能为 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "调账原因必填");
        }
        PetWalletAdjustment adjustment = new PetWalletAdjustment();
        adjustment.setUserId(targetUserId);
        adjustment.setDelta(delta);
        adjustment.setReason(reason.strip());
        adjustment.setTicketNo(ticketNo);
        adjustment.setRequestedBy(operatorAdminId);
        adjustment.setStatus("PENDING");
        adjustment.setVersion(0L);
        adjustmentMapper.insert(adjustment);
        log.info("调账申请创建, adjustmentId={}, targetUser={}, delta={}, operator={}",
                adjustment.getId(), targetUserId, delta, operatorAdminId);
        return adjustment;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PetWalletAdjustment approve(Long adjustmentId, Long operatorAdminId, String reason) {
        PetWalletAdjustment adjustment = requireReviewable(adjustmentId, operatorAdminId);
        if (adjustment == null) {
            // 重复审批：返回原结果（幂等，不产生第二次资金变动）
            return adjustmentMapper.selectById(adjustmentId);
        }
        if (!casSettle(adjustment, operatorAdminId, "APPROVED")) {
            // 并发双审：另一管理员已处理，返回其结果（不产生第二次资金变动）
            return adjustmentMapper.selectById(adjustmentId);
        }
        adjustment.setStatus("APPROVED");
        adjustment.setApprovedBy(operatorAdminId);
        adjustment.setVersion(adjustment.getVersion() + 1);
        // 仅 CAS 胜者入账：原子（失败整体回滚含审批状态）。
        // 补发（delta>0）走 ADJUSTMENT credit——冻结账户允许入账（§5.3）；
        // 扣回（delta<0）走 SPEND 语义的 debit——冻结账户自然拒绝（不能从冻结账户扣钱）。
        String operationId = "pw_adj_" + adjustment.getId();
        PetWalletResult wallet;
        if (adjustment.getDelta() > 0) {
            wallet = walletService.credit(new PetWalletCommand(
                    adjustment.getUserId(), null, "ADJUSTMENT", "ADJUSTMENT",
                    String.valueOf(adjustment.getId()), adjustment.getDelta(), operationId,
                    "adjust:" + adjustment.getId(), null, null, null));
        } else {
            wallet = walletService.debit(new PetWalletCommand(
                    adjustment.getUserId(), null, "SPEND", "ADJUSTMENT",
                    String.valueOf(adjustment.getId()), -adjustment.getDelta(), operationId,
                    "adjust:" + adjustment.getId(), null, null, null));
        }
        adjustment.setTransactionId(wallet.transactionId());
        adjustmentMapper.updateById(adjustment);
        log.info("调账审批入账完成, adjustmentId={}, delta={}, transactionId={}, approver={}",
                adjustment.getId(), adjustment.getDelta(), wallet.transactionId(), operatorAdminId);
        return adjustment;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PetWalletAdjustment reject(Long adjustmentId, Long operatorAdminId, String reason) {
        PetWalletAdjustment adjustment = requireReviewable(adjustmentId, operatorAdminId);
        if (adjustment == null) {
            return adjustmentMapper.selectById(adjustmentId);
        }
        casSettle(adjustment, operatorAdminId, "REJECTED");
        return adjustmentMapper.selectById(adjustmentId);
    }

    /**
     * 审批前置校验。
     * @return null=重复审批（已非 PENDING，幂等返回原结果）；否则返回待审记录
     */
    private PetWalletAdjustment requireReviewable(Long adjustmentId, Long operatorAdminId) {
        validateOperator(operatorAdminId);
        PetWalletAdjustment adjustment = adjustmentMapper.selectById(adjustmentId);
        if (adjustment == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "调账申请不存在");
        }
        if (operatorAdminId.equals(adjustment.getRequestedBy())) {
            // §4.1：申请人与审批人不同
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "调账申请不能由本人审批");
        }
        if (!"PENDING".equals(adjustment.getStatus())) {
            log.info("调账重复审批命中原状态, adjustmentId={}, status={}", adjustmentId, adjustment.getStatus());
            return null;
        }
        return adjustment;
    }

    /** CAS 抢占审批权（PENDING→目标状态），并发双审单胜 */
    private boolean casSettle(PetWalletAdjustment adjustment, Long operatorAdminId, String targetStatus) {
        long newVersion = adjustment.getVersion() + 1;
        int updated = adjustmentMapper.update(null, new LambdaUpdateWrapper<PetWalletAdjustment>()
                .set(PetWalletAdjustment::getStatus, targetStatus)
                .set(PetWalletAdjustment::getApprovedBy, operatorAdminId)
                .set(PetWalletAdjustment::getVersion, newVersion)
                .set(PetWalletAdjustment::getReviewedAt, LocalDateTime.now(ZoneOffset.UTC))
                .eq(PetWalletAdjustment::getId, adjustment.getId())
                .eq(PetWalletAdjustment::getVersion, adjustment.getVersion())
                .eq(PetWalletAdjustment::getStatus, "PENDING"));
        return updated == 1;
    }

    @Override
    public PetWalletAdjustment findById(Long adjustmentId) {
        return adjustmentMapper.selectById(adjustmentId);
    }

    @Override
    public List<PetWalletAdjustment> list(String status, Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<PetWalletAdjustment> wrapper = new LambdaQueryWrapper<PetWalletAdjustment>()
                .orderByDesc(PetWalletAdjustment::getId)
                .last("LIMIT " + safeSize + " OFFSET " + (long) (safePage - 1) * safeSize);
        if (status != null && !status.isBlank()) {
            wrapper.eq(PetWalletAdjustment::getStatus, status.toUpperCase());
        }
        if (userId != null) {
            wrapper.eq(PetWalletAdjustment::getUserId, userId);
        }
        return adjustmentMapper.selectList(wrapper);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void setAccountStatus(Long userId, boolean frozen, String reason,
                                 Long expectedVersion, Long operatorAdminId) {
        validateOperator(operatorAdminId);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "冻结/解冻原因必填");
        }
        PetWalletAccount account = accountMapper.selectOne(new LambdaQueryWrapper<PetWalletAccount>()
                .eq(PetWalletAccount::getUserId, userId)
                .eq(PetWalletAccount::getCurrency, PetWalletServiceImpl.CURRENCY_PET_COIN));
        if (account == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "钱包账户不存在");
        }
        String targetStatus = frozen ? "FROZEN" : "ACTIVE";
        LambdaUpdateWrapper<PetWalletAccount> wrapper = new LambdaUpdateWrapper<PetWalletAccount>()
                .set(PetWalletAccount::getStatus, targetStatus)
                .eq(PetWalletAccount::getId, account.getId())
                .eq(PetWalletAccount::getStatus, frozen ? "ACTIVE" : "FROZEN");
        if (expectedVersion != null) {
            // §8.4：expectedVersion 必带时 CAS 校验，防止并发操作互相覆盖
            wrapper.eq(PetWalletAccount::getVersion, expectedVersion);
        }
        int updated = accountMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "账户状态已被他人变更，请刷新后重试");
        }
        log.info("钱包账户冻结状态变更, userId={}, target={}, reason={}, operator={}",
                userId, targetStatus, reason, operatorAdminId);
    }

    private void validateOperator(Long operatorAdminId) {
        if (operatorAdminId == null || operatorAdminId <= 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "管理员身份缺失");
        }
    }
}
