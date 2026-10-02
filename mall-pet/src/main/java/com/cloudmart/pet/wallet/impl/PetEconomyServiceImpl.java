package com.cloudmart.pet.wallet.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.service.impl.PetOperationService;
import com.cloudmart.pet.service.impl.PetOperationService.WalletSettlement;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
import com.cloudmart.pet.config.PetRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;
import java.util.UUID;

/**
 * 宠物经济结算门面实现（W02）：LEGACY→旧链路、PET→独立钱包、PAUSED→维护拒绝。
 *
 * <p>事实键语义两模式对齐（P02 键分离）：EARN 业务事实键 = bizType:parts（不含客户端键）；
 * SPEND 购买意图键 = bizType:parts:客户端键（缺键回退每请求服务端键）。PET 模式下
 * 同一事实的重复请求由钱包 uk(user,bizType,bizKey) 收敛返回原结果。</p>
 */
@Service
@Slf4j
public class PetEconomyServiceImpl implements PetEconomyService {

    private final PetProperties properties;
    private final PetOperationService legacyOperationService;
    private final PetWalletService walletService;
    private final PetRequestDedupService dedupService;
    private final WishFeignClient wishFeignClient;

    public PetEconomyServiceImpl(PetProperties properties,
                                 PetOperationService legacyOperationService,
                                 PetWalletService walletService,
                                 PetRequestDedupService dedupService,
                                 WishFeignClient wishFeignClient) {
        this.properties = properties;
        this.legacyOperationService = legacyOperationService;
        this.walletService = walletService;
        this.dedupService = dedupService;
        this.wishFeignClient = wishFeignClient;
    }

    @Override
    public WalletSettlement earn(Long userId, Long petId, String bizType, Long bizRefId,
                                 long amount, String rewardSnapshot, Object... keyParts) {
        return settle(userId, petId, bizType, bizRefId, amount, rewardSnapshot, keyParts, false);
    }

    @Override
    public WalletSettlement spend(Long userId, Long petId, String bizType, Long bizRefId,
                                  long amount, String rewardSnapshot, Object... keyParts) {
        return settle(userId, petId, bizType, bizRefId, amount, rewardSnapshot, keyParts, true);
    }

    private WalletSettlement settle(Long userId, Long petId, String bizType, Long bizRefId,
                                    long amount, String rewardSnapshot, Object[] keyParts, boolean spend) {
        Mode mode = mode();
        if (amount <= 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "宠物收支金额必须为正");
        }
        return switch (mode) {
            case PAUSED -> throw new BusinessException(PetErrorCodes.PET_WALLET_MAINTENANCE,
                    "宠物收支维护中，请稍后再试（进行中请求不受影响）");
            case LEGACY -> settleLegacy(userId, petId, bizType, bizRefId, amount, rewardSnapshot,
                    keyParts, spend);
            case PET -> settlePet(userId, petId, bizType, amount, rewardSnapshot, keyParts, spend);
        };
    }

    /** 切换前：社区星光 + pet_operation 幂等/恢复（行为与切换前一致） */
    private WalletSettlement settleLegacy(Long userId, Long petId, String bizType, Long bizRefId,
                                          long amount, String rewardSnapshot, Object[] keyParts,
                                          boolean spend) {
        String operationKey = spend
                ? legacyOperationService.requestOperationKey(bizType, keyParts)
                : legacyOperationService.operationKey(bizType, keyParts);
        return spend
                ? legacyOperationService.executeSpend(operationKey, userId, petId, bizType, bizRefId,
                        (int) amount, rewardSnapshot)
                : legacyOperationService.executeEarn(operationKey, userId, petId, bizType, bizRefId,
                        (int) amount, rewardSnapshot);
    }

    /** 切换后：独立宠物币钱包同库同事务（无远程 UNKNOWN；明确失败抛出回滚调用方事务） */
    private WalletSettlement settlePet(Long userId, Long petId, String bizType, long amount,
                                       String rewardSnapshot, Object[] keyParts, boolean spend) {
        StringJoiner joiner = new StringJoiner(":");
        for (Object part : keyParts) {
            joiner.add(String.valueOf(part));
        }
        if (spend) {
            // 购买意图键：与 LEGACY requestOperationKey 同语义（同意图重试收敛，新意图独立扣款）
            String clientKey = PetRequestContext.idempotencyKey();
            joiner.add(clientKey != null && !clientKey.isBlank() ? clientKey.strip() : UUID.randomUUID().toString());
        }
        String bizKey = joiner.toString();
        String operationId = "pw_" + dedupService.canonicalHash(bizType + "|" + userId + "|" + bizKey);

        PetWalletCommand command = new PetWalletCommand(userId, petId,
                spend ? PetWalletServiceImpl.DIRECTION_SPEND : PetWalletServiceImpl.DIRECTION_EARN,
                bizType, bizKey, amount, operationId,
                dedupService.canonicalHash(bizType, userId, bizKey), null, null, rewardSnapshot);
        PetWalletResult result = spend ? walletService.debit(command) : walletService.credit(command);
        log.info("宠物币收支提交(PET), operationId={}, userId={}, bizType={}, bizKey={}, amount={}, duplicate={}",
                operationId, userId, bizType, bizKey, amount, result.duplicate());
        // P03：PET 币域 long 贯通，禁止 (int) 强转窄化隐藏高余额溢出
        return new WalletSettlement("COMPLETED", result.amount(),
                result.balanceAfter(), result.duplicate(), null);
    }

    @Override
    public Long balanceOf(Long userId) {
        return switch (mode()) {
            case PET -> walletService.getOrCreateAccount(userId).getBalance();
            case LEGACY -> starlightBalanceQuietly(userId);
            case PAUSED -> null;
        };
    }

    private Long starlightBalanceQuietly(Long userId) {
        try {
            Integer starlight = wishFeignClient.starlightBalance(userId).data();
            return starlight == null ? null : starlight.longValue();
        } catch (Exception e) {
            // 展示型数据 Fail-Open：null=前端隐藏余额，不阻断浏览
            return null;
        }
    }

    @Override
    public BusinessException settlementPending() {
        return legacyOperationService.settlementPending();
    }

    @Override
    public Mode mode() {
        // P03：PET_COIN 独立钱包为唯一正账本——null/blank/非法配置一律 PET
        //（LEGACY 仅作为显式配置的存量回退通道，不再作为任何缺省值）
        String raw = properties.getWalletMode();
        try {
            return Mode.valueOf(raw == null || raw.isBlank() ? "PET" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("pet.wallet.mode 配置非法: {}，按新基线 PET_COIN 处理", raw);
            return Mode.PET;
        }
    }
}
