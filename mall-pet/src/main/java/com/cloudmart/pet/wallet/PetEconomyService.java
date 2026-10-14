package com.cloudmart.pet.wallet;

/**
 * 宠物经济结算门面（W02/§6.3）：全部玩法收支的统一入口。
 *
 * <ul>
 *   <li><b>PET</b>（默认，唯一正账本）：本地独立钱包 {@link PetWalletService} 同库同事务——
 *       结算只有两种结果：成功返回 {@link WalletSettlement}，或明确失败（冻结/不足/维护）
 *       抛 BusinessException 回滚调用方事务，不存在 UNKNOWN/处理中中间态（C12）；</li>
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
    WalletSettlement earn(Long userId, Long petId, String bizType,
                          long amount, String rewardSnapshot, Object... keyParts);

    /** 幂等扣款（SPEND）：keyParts + 客户端请求键构成购买意图键（新意图独立扣款，重试收敛原单） */
    WalletSettlement spend(Long userId, Long petId, String bizType,
                           long amount, String rewardSnapshot, Object... keyParts);

    /**
     * 余额展示：PET 返回宠物币余额（long 防高余额 int 溢出）；PAUSED 返回 null（维护中不展示）。
     */
    Long balanceOf(Long userId);

    /** 当前钱包模式 */
    Mode mode();

    enum Mode { PAUSED, PET }

    /**
     * 同库同事务结算结果：返回即成功入账（C12：金额 long 防溢出；duplicate=重放命中原事实）。
     * 失败不产生结果对象——直接由钱包抛出 BusinessException 回滚调用方事务。
     */
    record WalletSettlement(long credited, Long balanceAfter, boolean duplicate) {
    }
}
