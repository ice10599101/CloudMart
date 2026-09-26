package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.feign.WishFeignClient.PetWalletOperationVO;
import com.cloudmart.pet.util.PetJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.StringJoiner;

/**
 * 业务操作服务（B01）：远程星光交易的幂等执行入口。
 *
 * <p>核心时序（不可回滚的远程调用必须发生在持久化操作记录提交之后）：</p>
 * <ol>
 *   <li>{@link PetOperationStore#claim} 以 REQUIRES_NEW 提交 PENDING 操作记录，
 *       占住确定性 operationId（并发同请求在此收敛）；</li>
 *   <li>携带同一 operationId 调用钱包幂等端点（钱包端按业务操作键原子去重）；</li>
 *   <li>按结果在调用方当前事务回写状态：COMPLETED / FAILED（明确失败，如余额不足）/
 *       UNKNOWN（超时、服务不可用等结果未知——恢复任务按原单号查询钱包或幂等重试，
 *       禁止换单号再次交易）。</li>
 * </ol>
 *
 * <p>调用方约定：SPEND（购买/进化/晋升扣款）在本地效果之前执行，UNKNOWN 抛
 * {@link #settlementPending()}（503，客户端按原请求重试幂等）；EARN（领奖发薪）在本地
 * 奖励事务之内、提交之前执行，UNKNOWN 不回滚本地奖励，对外返回"奖励结算中"（§2.3）。</p>
 */
@Component
@Slf4j
public class PetOperationService {

    /** 操作键最大长度（uk 列 VARCHAR(80)），超长时折叠为 前缀:SHA-256 */
    private static final int OPERATION_KEY_MAX = 80;

    private final PetOperationStore operationStore;
    private final WishFeignClient wishFeignClient;

    public PetOperationService(PetOperationStore operationStore, WishFeignClient wishFeignClient) {
        this.operationStore = operationStore;
        this.wishFeignClient = wishFeignClient;
    }

    /**
     * 星光交易结算结果。
     *
     * @param status       COMPLETED / UNKNOWN / FAILED / COMPENSATED / COMPENSATING
     * @param credited     实际到账（EARN 封顶截断后；UNKNOWN/FAILED 时为 0 且不可信）
     * @param balanceAfter 操作后余额（UNKNOWN 时为 null——不能拿旧值冒充）
     * @param duplicate    是否重复请求命中原结果
     * @param lastError    失败/未知原因
     */
    public record WalletSettlement(String status, int credited, Integer balanceAfter,
                                   boolean duplicate, String lastError) {

        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        public boolean isUnknown() {
            return "UNKNOWN".equals(status);
        }
    }

    /**
     * 服务端确定性操作键：{@code BIZ:part1:part2...}。客户端携带 Idempotency-Key 时
     * 追加为最后一段；总长超限折叠为前缀+摘要，保证同一业务实例重试必然得到同一键。
     */
    public String operationKey(String bizType, Object... parts) {
        StringJoiner joiner = new StringJoiner(":");
        joiner.add(bizType);
        for (Object part : parts) {
            joiner.add(String.valueOf(part));
        }
        String clientKey = PetRequestContext.idempotencyKey();
        if (clientKey != null && !clientKey.isBlank()) {
            joiner.add(clientKey.strip());
        }
        String key = joiner.toString();
        if (key.length() <= OPERATION_KEY_MAX) {
            return key;
        }
        return bizType + ":" + sha256Hex(key);
    }

    /** 幂等扣款（购买/进化/晋升/家具）。UNKNOWN 由调用方转 {@link #settlementPending()}。 */
    public WalletSettlement executeSpend(String operationId, Long userId, Long petId,
                                         String bizType, Long bizRefId, int amount, String rewardSnapshot) {
        return execute(operationId, userId, petId, bizType, bizRefId, "SPEND", amount, rewardSnapshot);
    }

    /** 幂等发薪（领奖/对战/捞瓶/任务）。UNKNOWN 返回结算中状态，本地奖励照常提交。 */
    public WalletSettlement executeEarn(String operationId, Long userId, Long petId,
                                        String bizType, Long bizRefId, int amount, String rewardSnapshot) {
        return execute(operationId, userId, petId, bizType, bizRefId, "EARN", amount, rewardSnapshot);
    }

    /** 专用补偿入口：按原单号派生的退款操作键，保证退款幂等且可关联原单 */
    public String refundOperationId(String originalOperationId) {
        return originalOperationId + ":refund";
    }

    private WalletSettlement execute(String operationId, Long userId, Long petId, String bizType,
                                     Long bizRefId, String direction, int amount, String rewardSnapshot) {
        if (amount <= 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "星光交易金额必须为正");
        }
        PetOperation operation;
        try {
            operation = operationStore.claim(operationId, userId, petId, bizType, bizRefId,
                    direction, amount, rewardSnapshot);
        } catch (PetOperationStore.PetOperationPendingException e) {
            throw settlementPending();
        }

        if ("COMPLETED".equals(operation.getStatus())) {
            return parseSettlement(operation);
        }
        if ("COMPENSATED".equals(operation.getStatus()) || "COMPENSATING".equals(operation.getStatus())) {
            return new WalletSettlement(operation.getStatus(), 0, null, true,
                    "原操作已退款/退款中，请按业务规则重新发起");
        }
        if ("FAILED".equals(operation.getStatus())) {
            return new WalletSettlement("FAILED", 0, null, true,
                    operation.getLastError() != null ? operation.getLastError() : "操作曾明确失败");
        }
        // PENDING / UNKNOWN：远程调用未完成，按原单幂等重入（钱包端去重兜底并发双打）
        try {
            PetWalletOperationVO result = ("SPEND".equals(direction)
                    ? wishFeignClient.spendStarlightIdempotent(userId, amount, bizRefId, operationId)
                    : wishFeignClient.earnStarlightIdempotent(userId, amount, bizRefId, operationId)).data();
            operationStore.markCompleted(operation, PetJsonUtils.toJson(result));
            return new WalletSettlement("COMPLETED",
                    result.creditedAmount() == null ? 0 : result.creditedAmount(),
                    result.balanceAfter(), result.duplicate(), null);
        } catch (BusinessException e) {
            // 明确失败：余额不足(402)/请求内容冲突(409)——记录后原样抛出，调用方本地事务回滚
            operationStore.markTerminal(operation, "FAILED", e.getMessage(), null);
            throw e;
        } catch (Exception e) {
            // 结果未知：超时/服务不可用/网络中断——标 UNKNOWN 交恢复任务按原单收敛
            log.warn("星光交易结果未知, operationId={}, type={}", operationId, direction, e);
            operationStore.markTerminal(operation, "UNKNOWN",
                    e.getClass().getSimpleName() + ": " + e.getMessage(), null);
            return new WalletSettlement("UNKNOWN", 0, null, false, "星光服务结果未知，奖励结算中");
        }
    }

    private WalletSettlement parseSettlement(PetOperation operation) {
        try {
            PetWalletOperationVO result = PetJsonUtils.parse(operation.getWalletResult(),
                    new TypeReference<PetWalletOperationVO>() {
                    });
            return new WalletSettlement("COMPLETED",
                    result.creditedAmount() == null ? 0 : result.creditedAmount(),
                    result.balanceAfter(), true, null);
        } catch (Exception e) {
            log.warn("操作结果快照解析失败, operationId={}", operation.getOperationId(), e);
            return new WalletSettlement("COMPLETED", operation.getAmount(), null, true, null);
        }
    }

    /** UNKNOWN 状态的业务异常（购买类调用方语义：处理中，按原请求重试幂等） */
    public BusinessException settlementPending() {
        return new BusinessException(PetErrorCodes.PET_SETTLEMENT_PENDING,
                "星光结算处理中，请稍后按原操作查询结果，勿重复下单");
    }

    /** 钱包结果查询（恢复任务用）；查询失败返回 null（结果未知） */
    public PetWalletOperationVO queryWallet(String operationId) {
        try {
            return wishFeignClient.findOperation(operationId).data();
        } catch (Exception e) {
            log.warn("钱包交易结果查询失败, operationId={}", operationId, e);
            return null;
        }
    }

    /** 当前 UTC 时间（恢复任务退避计算用） */
    public static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }
}
