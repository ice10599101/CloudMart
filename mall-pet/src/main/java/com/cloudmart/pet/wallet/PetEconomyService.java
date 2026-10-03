package com.cloudmart.pet.wallet;

import com.cloudmart.common.exception.BusinessException;

/**
 * 宠物经济结算门面（W02/§6.3）：全部玩法收支的统一入口。
 *
 * <ul>
 *   <li><b>PET</b>（默认，唯一正账本）：本地独立钱包 {@link PetWalletService} 同库同事务——
 *       无远程 UNKNOWN，明确失败（冻结/不足）抛 BusinessException 回滚调用方事务；</li>
 *   <li><b>PAUSED</b>（维护窗口）：拒绝新的宠物币收支请求（PET_WALLET_MAINTENANCE），
 *       等待在途事务收敛。</li>
 * </ul>
 *
 * <p>P03 二阶段：LEGACY（社区星光 + pet_operation）分支与底层机制已物理删除——
 * 上线时 pet_operation 表 0 行、PET 账本为唯一活账本，无回滚面。模式经 Nacos
 * 持久化；未知/空值一律按 PET 处理（唯一正账本，不猜币种）。</p>
 */
public interface PetEconomyService {

    /** 幂等发奖（EARN）：keyParts 构成业务事实键 */
    WalletSettlement earn(Long userId, Long petId, String bizType, Long bizRefId,
                          long amount, String rewardSnapshot, Object... keyParts);

    /** 幂等扣款（SPEND）：keyParts + 客户端请求键构成购买意图键（新意图独立扣款，重试收敛原单） */
    WalletSettlement spend(Long userId, Long petId, String bizType, Long bizRefId,
                           long amount, String rewardSnapshot, Object... keyParts);

    /**
     * 余额展示：PET 返回宠物币余额（long 防高余额 int 溢出）；PAUSED 返回 null（维护中不展示）。
     */
    Long balanceOf(Long userId);

    /** 结算处理中异常（购买类调用方在在途请求未收敛时抛出） */
    BusinessException settlementPending();

    /** 当前钱包模式 */
    Mode mode();

    enum Mode { PAUSED, PET }

    /** 结算结果（P01/P03：金额 long 防溢出；duplicate=重放命中原事实） */
    record WalletSettlement(String status, long credited, Long balanceAfter,
                            boolean duplicate, String lastError) {

        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        public boolean isUnknown() {
            return "UNKNOWN".equals(status);
        }
    }
}
