package com.cloudmart.pet.wallet;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.service.impl.PetOperationService;

/**
 * 宠物经济结算门面（W02/§6.3）：全部玩法收支的统一入口，按 {@code pet.wallet.mode} 路由。
 *
 * <ul>
 *   <li><b>LEGACY</b>（默认，切换前）：委托旧链路 {@link PetOperationService}——社区星光 +
 *       pet_operation 幂等/恢复任务，行为与切换前完全一致；</li>
 *   <li><b>PAUSED</b>（切换窗口）：拒绝新的宠物收支请求（PET_WALLET_MAINTENANCE），
 *       等待在途事务收敛；</li>
 *   <li><b>PET</b>（切换后）：本地独立钱包 {@link PetWalletService} 同库同事务——无远程 UNKNOWN，
 *       明确失败（冻结/不足）抛 BusinessException 回滚调用方事务。</li>
 * </ul>
 *
 * <p>模式经 Nacos 配置持久化（§6.3），切换窗口操作由发布流程执行；每个订单记录
 * walletDomain，旧恢复器只按 LEGACY_WISH 路由，不以全局开关猜币种。</p>
 */
public interface PetEconomyService {

    /** 幂等发奖（EARN）：keyParts 构成业务事实键（两模式同语义，P02 键分离已保证） */
    PetOperationService.WalletSettlement earn(Long userId, Long petId, String bizType, Long bizRefId,
                                              long amount, String rewardSnapshot, Object... keyParts);

    /** 幂等扣款（SPEND）：keyParts + 客户端请求键构成购买意图键（新意图独立扣款，重试收敛原单） */
    PetOperationService.WalletSettlement spend(Long userId, Long petId, String bizType, Long bizRefId,
                                               long amount, String rewardSnapshot, Object... keyParts);

    /**
     * 余额展示：PET 返回宠物币余额（P03：long 防高余额 int 溢出）；LEGACY 返回社区星光
     * （降级为 null=Fail-Open 隐藏）；PAUSED 返回 null（维护中不展示余额）。
     */
    Long balanceOf(Long userId);

    /** LEGACY 未知结算异常（购买类调用方语义；PET 模式不会走到） */
    BusinessException settlementPending();

    /** 当前钱包模式 */
    Mode mode();

    enum Mode { LEGACY, PAUSED, PET }
}
