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
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 业务操作服务（B01/P02）：<b>LEGACY_WISH 域专用</b>（W02/§5.4）——社区星光旧链路的
 * 幂等执行与恢复语义；仅可经 {@link com.cloudmart.pet.wallet.PetEconomyService} 在
 * wallet.mode=LEGACY 时到达，新业务一律走独立钱包 {@link com.cloudmart.pet.wallet.PetWalletService}。
 * 切换完成且旧单收敛后整体下线（下线条件：恢复扫描 0 待决 + 旧客户端淘汰）。
 *
 * <p>远程星光交易的幂等执行入口。
 *
 * <p>核心时序（不可回滚的远程调用必须发生在持久化操作记录提交之后）：</p>
 * <ol>
 *   <li>{@link PetOperationStore#claim} 以 REQUIRES_NEW 提交 PENDING 操作记录，
 *       占住确定性 operationId（并发同请求在此收敛）；</li>
 *   <li>携带同一 operationId 调用钱包幂等端点（钱包端按业务操作键原子去重）；</li>
 *   <li>按结果在调用方当前事务回写状态：COMPLETED / FAILED（明确业务拒绝）/ UNKNOWN
 *       （结果未知——恢复任务按原单号查询钱包或幂等重试，禁止换单号再次交易）。</li>
 * </ol>
 *
 * <p>幂等键两层（P02/TX-01）：</p>
 * <ul>
 *   <li><b>业务事实键</b> {@link #operationKey}：EARN 一次性领奖事实（bizType+业务实例），
 *       不掺客户端键——用户换请求键不能改变"同一业务事实只结算一次"；</li>
 *   <li><b>请求意图键</b> {@link #requestOperationKey}：SPEND 购买意图（业务内容+客户端键，
 *       缺键回退每请求一个服务端键），同一意图重试收敛原单，新意图独立扣款。</li>
 * </ul>
 *
 * <p>错误分类（P02/TX-02）：仅"余额不足/键冲突"等业务明确拒绝记 FAILED 并原样抛出；
 * 服务不可用/超时等结果未知一律 UNKNOWN 交恢复任务收敛，禁止把降级异常当业务失败。
 * 调用方约定：SPEND（购买/进化/晋升扣款）在本地效果之前执行，UNKNOWN 抛
 * {@link #settlementPending()}（503，客户端按原请求重试幂等）；EARN（领奖发薪）在本地
 * 奖励事务之内、提交之前执行，UNKNOWN 不回滚本地奖励，对外返回"奖励结算中"（§2.3）。</p>
 */
@Component
@Slf4j
public class PetOperationService {

    /** 操作键最大长度（uk 列 VARCHAR(160)，P02/TX-05 扩容），超长时折叠为 前缀:SHA-256 */
    private static final int OPERATION_KEY_MAX = 160;

    /**
     * 业务明确拒绝的错误码（TX-02）：远程钱包已裁决、重试必然同结果。
     * 其余（WISH_SERVICE_UNAVAILABLE/超时/网络/未知信封）一律视为结果未知。
     */
    private static final Set<String> DEFINITE_REJECTION_CODES = Set.of(
            "WISH_STARLIGHT_INSUFFICIENT", "WISH_OPERATION_CONFLICT");

    private final PetOperationStore operationStore;
    private final WishFeignClient wishFeignClient;
    private final com.cloudmart.pet.config.PetMetrics metrics;
    private final com.cloudmart.pet.config.PetProperties petProperties;

    public PetOperationService(PetOperationStore operationStore, WishFeignClient wishFeignClient,
                               com.cloudmart.pet.config.PetMetrics metrics,
                               com.cloudmart.pet.config.PetProperties petProperties) {
        this.operationStore = operationStore;
        this.wishFeignClient = wishFeignClient;
        this.metrics = metrics;
        this.petProperties = petProperties;
    }

    /**
     * 星光交易结算结果。
     *
     * @param status       COMPLETED / UNKNOWN / FAILED / COMPENSATED / COMPENSATING / MANUAL_REVIEW
     * @param credited     实际到账（EARN 封顶截断后；UNKNOWN/FAILED 时为 0 且不可信）
     * @param balanceAfter 操作后余额（UNKNOWN 时为 null——不能拿旧值冒充）
     * @param duplicate    是否重复请求命中原结果
     * @param lastError    失败/未知原因
     */
    public record WalletSettlement(String status, long credited, Long balanceAfter,
                                   boolean duplicate, String lastError) {

        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        public boolean isUnknown() {
            return "UNKNOWN".equals(status);
        }
    }

    /**
     * 业务事实键（EARN 一次性领奖）：{@code BIZ:part1:part2...}，与客户端请求键无关——
     * 同一业务实例的任意重试/换键请求收敛到同一操作，恢复任务按事实结算。
     */
    public String operationKey(String bizType, Object... parts) {
        return buildKey(bizType, parts, null);
    }

    /**
     * 请求意图键（SPEND 购买）：业务内容 + 客户端 Idempotency-Key；客户端缺键时
     * 每请求生成服务端键（等价旧语义：每个请求是独立购买意图；重试收敛依赖客户端持久化键，
     * FE-03 落地）。同一键的并发/重试请求收敛到同一笔扣款。
     */
    public String requestOperationKey(String bizType, Object... parts) {
        String clientKey = PetRequestContext.idempotencyKey();
        return buildKey(bizType, parts, clientKey != null && !clientKey.isBlank()
                ? clientKey : UUID.randomUUID().toString());
    }

    private String buildKey(String bizType, Object[] parts, String clientKey) {
        StringJoiner joiner = new StringJoiner(":");
        joiner.add(bizType);
        for (Object part : parts) {
            joiner.add(String.valueOf(part));
        }
        if (clientKey != null) {
            joiner.add(clientKey);
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

    /**
     * 退款操作键（P02/TX-04）：固定长度摘要派生，与原单一一对应且不受原单键长影响
     * （原键+":refund" 后缀在 160 上限下可能溢出，摘要派生从根上消除）。
     */
    public String refundOperationId(String originalOperationId) {
        return "REFUND:" + sha256Hex(originalOperationId);
    }

    /** @return 该异常是否为远程钱包的明确业务拒绝（重试必然同结果，TX-02） */
    static boolean isDefiniteRejection(BusinessException e) {
        return e.getCode() != null && DEFINITE_REJECTION_CODES.contains(e.getCode());
    }

    /**
     * TX-02 补强：Feign 非 2xx 响应（未走 fallback 的原始 FeignException）按 HTTP 状态与
     * 响应信封 error.code 分类——402/409 属明确拒绝；其余（5xx/连接/超时）结果未知。
     * @return 明确拒绝时返回要抛出的 BusinessException；null 表示非 Feign 或结果未知
     */
    static BusinessException definiteFromFeign(Throwable e) {
        if (!(e instanceof feign.FeignException feignException)) {
            return null;
        }
        int status = feignException.status();
        if (status != 402 && status != 409) {
            return null;
        }
        // 优先解析响应信封的 error.code（wish 返回标准信封）；解析失败按 HTTP 状态兜底
        String code = status == 402 ? "WISH_STARLIGHT_INSUFFICIENT" : "WISH_OPERATION_CONFLICT";
        try {
            String body = feignException.contentUTF8();
            if (body != null && !body.isBlank()) {
                com.fasterxml.jackson.databind.JsonNode error = PET_MAPPER.readTree(body).path("error");
                String envelopeCode = error.path("code").asText("");
                if (!envelopeCode.isEmpty()) {
                    code = envelopeCode;
                }
            }
        } catch (Exception ignored) {
            // 信封解析失败，按 HTTP 状态兜底
        }
        return new BusinessException(code, feignException.getMessage());
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper PET_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private WalletSettlement execute(String operationId, Long userId, Long petId, String bizType,
                                     Long bizRefId, String direction, int amount, String rewardSnapshot) {
        if (amount <= 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "星光交易金额必须为正");
        }
        // §9.3 可回退开关：关闭幂等交易跳过本地操作记录，直接走钱包幂等端点
        // （钱包端仍按 operationId 去重，但失去 pet_operation 审计与恢复任务兜底，仅应急回退用）
        if (!petProperties.getFeatureSwitches().isWalletIdempotent()) {
            try {
                PetWalletOperationVO vo = ("SPEND".equals(direction)
                        ? wishFeignClient.spendStarlightIdempotent(userId, amount, bizRefId, operationId).data()
                        : wishFeignClient.earnStarlightIdempotent(userId, amount, bizRefId, operationId).data());
                return new WalletSettlement("COMPLETED",
                        vo == null || vo.creditedAmount() == null ? 0L : vo.creditedAmount().longValue(),
                        vo == null ? null : vo.balanceAfter() == null ? null : vo.balanceAfter().longValue(),
                        vo != null && vo.duplicate(), null);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("旧链路交易失败, operationId={}", operationId, e);
                return new WalletSettlement("UNKNOWN", 0, null, false, "旧链路结果未知");
            }
        }
        PetOperation operation;
        try {
            operation = operationStore.claim(operationId, userId, petId, bizType, bizRefId,
                    direction, amount, rewardSnapshot);
        } catch (PetOperationStore.PetOperationPendingException e) {
            throw settlementPending();
        }

        if ("COMPLETED".equals(operation.getStatus())) {
            WalletSettlement settlement = parseSettlement(operation);
            if (settlement != null) {
                return settlement;
            }
            // 结果快照损坏（P02）：远程已完成但无法还原实际到账——转人工核查，禁止拿金额冒充
            metrics.increment("pet_settlement_snapshot_corrupt", "direction", direction);
            operationStore.markManualReview(operation, "结果快照解析失败，需人工核对钱包实际到账");
            throw settlementPending();
        }
        if ("COMPENSATED".equals(operation.getStatus()) || "COMPENSATING".equals(operation.getStatus())) {
            return new WalletSettlement(operation.getStatus(), 0, null, true,
                    "原操作已退款/退款中，请按业务规则重新发起");
        }
        if ("MANUAL_REVIEW".equals(operation.getStatus())) {
            return new WalletSettlement("MANUAL_REVIEW", 0, null, true,
                    operation.getLastError() != null ? operation.getLastError() : "操作待人工核查");
        }
        if ("FAILED".equals(operation.getStatus())) {
            return new WalletSettlement("FAILED", 0, null, true,
                    operation.getLastError() != null ? operation.getLastError() : "操作曾明确失败");
        }
        // PENDING / PROCESSING / UNKNOWN：远程调用未完成，按原单幂等重入（钱包端去重兜底并发双打；
        // PROCESSING 表示恢复任务持租约处理中，同样按原单重入，钱包端幂等保证不二次生效）
        try {
            PetWalletOperationVO result = ("SPEND".equals(direction)
                    ? wishFeignClient.spendStarlightIdempotent(userId, amount, bizRefId, operationId)
                    : wishFeignClient.earnStarlightIdempotent(userId, amount, bizRefId, operationId)).data();
            if (result == null || !"COMPLETED".equals(result.status())) {
                // 信封成功但交易状态非完成（TX-02）：按未知处理，不冒充成功
                operationStore.markTerminal(operation, "UNKNOWN",
                        "钱包返回非完成状态: " + (result == null ? "null" : result.status()), null);
                return unknownSettlement(direction);
            }
            operationStore.markCompleted(operation, PetJsonUtils.toJson(result));
            return new WalletSettlement("COMPLETED",
                    result.creditedAmount() == null ? 0L : result.creditedAmount().longValue(),
                    result.balanceAfter() == null ? null : result.balanceAfter().longValue(),
                    result.duplicate(), null);
        } catch (BusinessException e) {
            if (!isDefiniteRejection(e)) {
                // 服务不可用/降级（TX-02）：结果未知，交恢复任务收敛，不当业务失败
                log.warn("星光交易结果未知(远程不可用), operationId={}, type={}", operationId, direction, e);
                metrics.increment("pet_settlement_unknown", "direction", direction);
                operationStore.markTerminal(operation, "UNKNOWN",
                        "远程不可用: " + e.getCode() + ": " + e.getMessage(), null);
                return unknownSettlement(direction);
            }
            // 明确失败：余额不足(402)/请求内容冲突(409)——记录后原样抛出，调用方本地事务回滚
            operationStore.markTerminal(operation, "FAILED", e.getMessage(), null);
            throw e;
        } catch (Exception e) {
            // TX-02 补强：Feign 402/409 是明确业务拒绝（重试同结果）——记 FAILED 并抛出
            BusinessException definite = definiteFromFeign(e);
            if (definite != null) {
                log.info("星光交易明确拒绝(HTTP), operationId={}, code={}", operationId, definite.getCode());
                operationStore.markTerminal(operation, "FAILED", definite.getCode() + ": " + definite.getMessage(), null);
                throw definite;
            }
            // 结果未知：超时/服务不可用/网络中断——标 UNKNOWN 交恢复任务按原单收敛
            log.warn("星光交易结果未知, operationId={}, type={}", operationId, direction, e);
            metrics.increment("pet_settlement_unknown", "direction", direction);
            operationStore.markTerminal(operation, "UNKNOWN",
                    e.getClass().getSimpleName() + ": " + e.getMessage(), null);
            return unknownSettlement(direction);
        }
    }

    private WalletSettlement unknownSettlement(String direction) {
        return new WalletSettlement("UNKNOWN", 0, null, false,
                "SPEND".equals(direction) ? "扣款结果未知，按原请求重试" : "星光服务结果未知，奖励结算中");
    }

    /**
     * 还原历史结果快照。解析失败返回 null（P02/TX-04：删除"用 operation.amount 冒充实际到账"
     * 的降级行为——快照损坏必须转人工，不得伪造到账金额）。
     */
    private WalletSettlement parseSettlement(PetOperation operation) {
        try {
            PetWalletOperationVO result = PetJsonUtils.parse(operation.getWalletResult(),
                    new TypeReference<PetWalletOperationVO>() {
                    });
            if (result == null) {
                return null;
            }
            return new WalletSettlement("COMPLETED",
                    result.creditedAmount() == null ? 0L : result.creditedAmount().longValue(),
                    result.balanceAfter() == null ? null : result.balanceAfter().longValue(),
                    true, null);
        } catch (Exception e) {
            log.error("操作结果快照解析失败, operationId={}", operation.getOperationId(), e);
            return null;
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
