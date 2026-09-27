package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletLedger;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletLedgerMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
import com.cloudmart.pet.wallet.PetWalletService;
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
 * 宠物币钱包服务实现（W01）。
 *
 * <p>核心不变量（§5.5）：</p>
 * <ul>
 *   <li>SELECT FOR UPDATE 串行化同账户写，条件 UPDATE（balance+version+status）原子扣减，
 *       影响行数=1 才生效（T04：并发扣款无负余额）；</li>
 *   <li>流水 + 账本 + 版本推进同一事务，账本 balance_after=balance_before+delta（T08 整体回滚）；</li>
 *   <li>Math.addExact/subtractExact 溢出即失败（超出范围整个事务失败并告警，不静默截断）；</li>
 *   <li>重复请求命中 uk(user,bizType,bizKey)/uk(operationId) → 返回原结果（T05/T07/T09）。</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetWalletServiceImpl implements PetWalletService {

    public static final String CURRENCY_PET_COIN = "PET_COIN";
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String DIRECTION_EARN = "EARN";
    public static final String DIRECTION_SPEND = "SPEND";
    public static final String DIRECTION_REFUND = "REFUND";
    public static final String DIRECTION_ADJUSTMENT = "ADJUSTMENT";

    private final PetWalletAccountMapper accountMapper;
    private final PetWalletTransactionMapper transactionMapper;
    private final PetWalletLedgerMapper ledgerMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PetWalletAccount getOrCreateAccount(Long userId) {
        PetWalletAccount account = accountMapper.selectOne(new LambdaQueryWrapper<PetWalletAccount>()
                .eq(PetWalletAccount::getUserId, userId)
                .eq(PetWalletAccount::getCurrency, CURRENCY_PET_COIN));
        if (account != null) {
            return account;
        }
        PetWalletAccount fresh = new PetWalletAccount();
        fresh.setUserId(userId);
        fresh.setCurrency(CURRENCY_PET_COIN);
        fresh.setBalance(0L);
        fresh.setStatus("ACTIVE");
        fresh.setVersion(0L);
        try {
            accountMapper.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException e) {
            // 并发懒创建：另一事务已建账，重读可见其结果
            return accountMapper.selectOne(new LambdaQueryWrapper<PetWalletAccount>()
                    .eq(PetWalletAccount::getUserId, userId)
                    .eq(PetWalletAccount::getCurrency, CURRENCY_PET_COIN));
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public PetWalletResult credit(PetWalletCommand command) {
        return execute(command);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public PetWalletResult debit(PetWalletCommand command) {
        return execute(command);
    }

    private PetWalletResult execute(PetWalletCommand command) {
        validate(command);
        try {
            return apply(command);
        } catch (DuplicateKeyException duplicate) {
            // 唯一键冲突（uk_operationId 或 uk_user_bizType_bizKey）：返回既有流水结果，不做任何变更（T05/T07/T09）
            PetWalletTransaction existing = findTransaction(command.operationId());
            if (existing == null) {
                // 事实键冲突（同业务事实换操作键重放）：按事实返回原结果
                existing = transactionMapper.selectOne(new LambdaQueryWrapper<PetWalletTransaction>()
                        .eq(PetWalletTransaction::getUserId, command.userId())
                        .eq(PetWalletTransaction::getBizType, command.bizType())
                        .eq(PetWalletTransaction::getBizKey, command.bizKey()));
            }
            if (existing == null) {
                // 冲突但行不可读：并发事务尚未提交
                throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS,
                        "钱包交易处理中，请按原请求查询结果");
            }
            if (!existing.getUserId().equals(command.userId())) {
                throw new BusinessException(PetErrorCodes.PET_OPERATION_CONFLICT, "操作键已存在但归属不同");
            }
            PetWalletLedger ledger = ledgerMapper.selectOne(new LambdaQueryWrapper<PetWalletLedger>()
                    .eq(PetWalletLedger::getTransactionId, existing.getId()));
            log.info("钱包重复请求命中原结果, operationId={}, userId={}, bizType={}",
                    command.operationId(), command.userId(), command.bizType());
            return new PetWalletResult(existing.getId(), existing.getOperationId(),
                    ledger == null ? 0L : ledger.getBalanceAfter(),
                    existing.getAmount(), existing.getStatus(), true);
        }
    }

    private void validate(PetWalletCommand command) {
        if (command.amount() <= 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "钱包金额必须为正整数");
        }
        if (command.bizKey() == null || command.bizKey().isBlank()
                || command.bizKey().length() > 160 || !isAscii(command.bizKey())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "业务唯一键非法（1..160 ASCII）");
        }
        if (command.operationId() == null || command.operationId().isBlank()
                || command.operationId().length() > 160 || !isAscii(command.operationId())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "操作键非法（1..160 ASCII）");
        }
    }

    private PetWalletResult apply(PetWalletCommand command) {
        // FOR UPDATE 串行化同账户并发写（T04）；懒建账户可见并发建账结果
        PetWalletAccount account = getOrCreateAccount(command.userId());
        account = lockAccount(account.getId());

        boolean frozen = "FROZEN".equals(account.getStatus());
        boolean refundLike = DIRECTION_REFUND.equals(command.direction())
                || DIRECTION_ADJUSTMENT.equals(command.direction());
        if (frozen && !refundLike) {
            // 冻结账户：允许退款与调账，普通消费/奖励禁止（§4.3/§5.3）
            throw new BusinessException(PetErrorCodes.PET_WALLET_FROZEN, "宠物币账户已冻结，请先联系客服处理");
        }

        long before = account.getBalance();
        long delta = switch (command.direction()) {
            case DIRECTION_SPEND -> -command.amount();
            case DIRECTION_EARN, DIRECTION_REFUND, DIRECTION_ADJUSTMENT -> command.amount();
            default -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "未知钱包方向: " + command.direction());
        };
        long after = delta >= 0 ? Math.addExact(before, delta) : Math.subtractExact(before, -delta);
        if (after < 0) {
            // 余额不足：不变余额、不写流水、不推进领奖（§8.2）
            throw new BusinessException(PetErrorCodes.PET_WALLET_INSUFFICIENT,
                    "宠物币不足，当前余额 " + before);
        }

        // 条件推进版本：CAS 兜底（FOR UPDATE 已串行化，双保险），0 行视为状态冲突
        long newVersion = Math.addExact(account.getVersion(), 1L);
        int updated = accountMapper.update(null, new LambdaUpdateWrapper<PetWalletAccount>()
                .set(PetWalletAccount::getBalance, after)
                .set(PetWalletAccount::getVersion, newVersion)
                .eq(PetWalletAccount::getId, account.getId())
                .eq(PetWalletAccount::getVersion, account.getVersion())
                .eq(PetWalletAccount::getCurrency, CURRENCY_PET_COIN));
        if (updated != 1) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "钱包账户并发冲突，请重试");
        }

        PetWalletTransaction transaction = new PetWalletTransaction();
        transaction.setOperationId(command.operationId());
        transaction.setUserId(command.userId());
        transaction.setPetId(command.petId());
        transaction.setCurrency(CURRENCY_PET_COIN);
        transaction.setBizType(command.bizType());
        transaction.setBizKey(command.bizKey());
        transaction.setDirection(command.direction());
        transaction.setAmount(command.amount());
        transaction.setStatus(STATUS_COMMITTED);
        transaction.setRequestHash(command.requestHash() == null ? "" : command.requestHash());
        transaction.setOriginalTransactionId(command.originalTransactionId());
        transaction.setRuleVersion(command.ruleVersion());
        transaction.setResultJson(command.resultJson());
        transaction.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        transactionMapper.insert(transaction);

        PetWalletLedger ledger = new PetWalletLedger();
        ledger.setTransactionId(transaction.getId());
        ledger.setAccountId(account.getId());
        ledger.setUserId(command.userId());
        ledger.setPetId(command.petId());
        ledger.setDelta(delta);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setAccountVersion(newVersion);
        ledger.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        ledgerMapper.insert(ledger);

        log.info("宠物币收支提交, operationId={}, userId={}, bizType={}, bizKey={}, direction={}, amount={}, after={}",
                command.operationId(), command.userId(), command.bizType(), command.bizKey(),
                command.direction(), command.amount(), after);
        return new PetWalletResult(transaction.getId(), transaction.getOperationId(), after,
                command.amount(), STATUS_COMMITTED, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public PetWalletResult refundFull(Long originalTransactionId, String refundOperationId, String operatorReason) {
        PetWalletTransaction original = transactionMapper.selectById(originalTransactionId);
        if (original == null || !STATUS_COMMITTED.equals(original.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_WALLET_REFUND_INVALID, "退款原单不存在或未提交");
        }
        if (!DIRECTION_SPEND.equals(original.getDirection())) {
            throw new BusinessException(PetErrorCodes.PET_WALLET_REFUND_INVALID, "退款原单不是扣款流水");
        }
        // 累计退款 ≤ 原实扣（首版全额且至多一次，§5.5）
        List<PetWalletTransaction> priorRefunds = transactionMapper.selectList(
                new LambdaQueryWrapper<PetWalletTransaction>()
                        .eq(PetWalletTransaction::getOriginalTransactionId, originalTransactionId)
                        .eq(PetWalletTransaction::getDirection, DIRECTION_REFUND)
                        .eq(PetWalletTransaction::getStatus, STATUS_COMMITTED));
        long refunded = priorRefunds.stream().mapToLong(PetWalletTransaction::getAmount).sum();
        if (refunded + original.getAmount() > original.getAmount()) {
            throw new BusinessException(PetErrorCodes.PET_WALLET_REFUND_INVALID,
                    "累计退款将超过原单实扣金额（已退 " + refunded + "）");
        }
        String resultJson = operatorReason == null ? null
                : com.cloudmart.pet.util.PetJsonUtils.toJson(java.util.Map.of("reason", operatorReason));
        PetWalletCommand command = new PetWalletCommand(original.getUserId(), original.getPetId(),
                DIRECTION_REFUND, "REFUND", originalTransactionId + ":FULL", original.getAmount(),
                refundOperationId, original.getRequestHash(), originalTransactionId,
                original.getRuleVersion(), resultJson);
        return execute(command);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public PetWalletTransaction findTransaction(String operationId) {
        return transactionMapper.selectOne(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getOperationId, operationId));
    }

    private PetWalletAccount lockAccount(Long accountId) {
        PetWalletAccount locked = accountMapper.selectOne(new LambdaQueryWrapper<PetWalletAccount>()
                .eq(PetWalletAccount::getId, accountId)
                .last("FOR UPDATE"));
        if (locked == null) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "钱包账户不存在");
        }
        return locked;
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

}
