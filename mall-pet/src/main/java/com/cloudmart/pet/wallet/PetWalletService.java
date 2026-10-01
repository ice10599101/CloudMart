package com.cloudmart.pet.wallet;

import com.cloudmart.pet.entity.PetWalletTransaction;

/**
 * 宠物币钱包服务（W01）：PET_COIN 收支的唯一入口。
 *
 * <p>事务边界（§5.3）：变更方法必须加入调用方业务事务（MANDATORY）——业务单、钱包、
 * 流水、账本、资产发放同一 MySQL 本地事务提交，任何一步失败整体回滚（T08）；
 * <b>禁止</b>照搬旧 PetOperationStore 的 REQUIRES_NEW 远程发币模式。</p>
 *
 * <p>幂等（§5.3 业务键表）：同一 {@code (userId, bizType, bizKey)} 事实至多一笔流水——
 * 唯一键冲突后重读既有流水返回原结果；客户端请求键不进入业务事实摘要。
 * operationId 由服务端生成（{@code pw_:64位摘要}），不接受客户端任意超长键。</p>
 */
public interface PetWalletService {

    /** 懒创建并返回本人账户（无宠物也可建账；新账户期初 0，无 0 金额流水） */
    com.cloudmart.pet.entity.PetWalletAccount getOrCreateAccount(Long userId);

    /** 正向入账（EARN 奖励/REFUND 退款/ADJUSTMENT 调账；FROZEN 仅允许 REFUND/ADJUSTMENT） */
    PetWalletResult credit(PetWalletCommand command);

    /** 负向扣款（SPEND；FROZEN 拒绝，余额不足拒绝且不产生任何变更） */
    PetWalletResult debit(PetWalletCommand command);

    /**
     * 按原扣款单全额退款（T10）：验证原单为本 COMMITTED 流水；同原单累计退款（首版全额且至多一次）；
     * 冻结账户允许退款；退款流水 original_transaction_id 关联原单。
     */
    PetWalletResult refundFull(Long originalTransactionId, String refundOperationId, String operatorReason);

    /** 按 operationId 查询流水（重复请求返回历史结果快照，不做任何变更） */
    com.cloudmart.pet.entity.PetWalletTransaction findTransaction(String operationId);

    /**
     * P01 事务边界外的重复结果解析（SUPPORTS/readOnly，禁止加入业务事务）：
     * 当 credit/debit 因唯一键冲突抛出 DuplicateKeyException 且业务事务已回滚后，
     * 调用方在本方法读取已提交的原结果；竞争事务尚未提交时抛 PET_REQUEST_IN_PROGRESS。
     */
    PetWalletResult resolveDuplicate(PetWalletCommand command);

    /** 收支命令（§5.3：客户端只允许意图，不接受金额/余额） */
    record PetWalletCommand(
            Long userId,
            Long petId,
            /** EARN/SPEND/REFUND/ADJUSTMENT */
            String direction,
            /** 业务类型（PURCHASE/ACTIVITY_REWARD/QUEST_REWARD/REFUND/ADJUSTMENT 等） */
            String bizType,
            /** 业务唯一键（§5.3 固定次序，服务端构造） */
            String bizKey,
            /** 金额（正整数；带符号由方向决定） */
            long amount,
            /** 服务器生成的操作键 */
            String operationId,
            /** 请求规范摘要（幂等冲突判定；无请求键时传业务事实摘要） */
            String requestHash,
            /** 退款/冲正关联的原交易 ID */
            Long originalTransactionId,
            /** 规则/配置版本快照 */
            String ruleVersion,
            /** 结果快照（物品等；不存临时签名链接） */
            String resultJson
    ) {
    }

    /** 收支结果（重复请求 duplicate=true 且返回历史结果） */
    record PetWalletResult(
            Long transactionId,
            String operationId,
            long balanceAfter,
            long amount,
            String status,
            boolean duplicate
    ) {
    }
}
