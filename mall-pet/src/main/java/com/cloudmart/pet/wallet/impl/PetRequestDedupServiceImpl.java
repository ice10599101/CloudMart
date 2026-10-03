package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 请求级幂等去重实现（W01/P02）。
 *
 * <p>claim 以 REQUIRES_NEW 先行提交 PROCESSING 行——请求键必须先于业务事务占住，
 * 调用方回滚不能连带抹掉（否则重试丢失收敛锚点）。行持有租约（owner/leaseUntil/version）：
 * 执行者崩溃留下的 PROCESSING 残留在租约到期后可被同键重试或恢复扫描器
 * （{@code PetPurchaseRecoveryService}）CAS 接管，不会永久卡死。</p>
 *
 * <p>终态回写（P02）为 REQUIRED：在购买业务事务内调用时与订单/钱包/资产同一 MySQL
 * 本地事务提交（消除"业务已提交而 dedup 残留 PROCESSING"的崩溃窗口）；
 * 在业务拒绝路径（无事务）调用时自成小事务，语义不变。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetRequestDedupServiceImpl implements PetRequestDedupService {

    private final PetRequestDedupMapper dedupMapper;

    /** 执行者租约时长：正常购买在毫秒级完成，超时残留即视为执行者已丢失 */
    @Value("${pet.request-dedup.lease-seconds:300}")
    private long leaseSeconds;

    /** 实例标识（接管轮换时区分新旧执行者） */
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClaimResult claim(Long userId, String endpointKey, String requestKey, String payloadHash) {
        if (!PetRequestDedupService.isValidRequestKey(requestKey)) {
            throw new BusinessException(PetErrorCodes.PET_REQUEST_KEY_INVALID,
                    "幂等键缺失或非法（16..128 ASCII）");
        }
        PetRequestDedup fresh = new PetRequestDedup();
        fresh.setUserId(userId);
        fresh.setEndpointKey(endpointKey);
        fresh.setRequestKey(requestKey);
        fresh.setPayloadHash(payloadHash);
        fresh.setStatus("PROCESSING");
        fresh.setLeaseOwner(leaseOwner());
        fresh.setLeaseUntil(leaseUntil());
        fresh.setVersion(0L);
        try {
            dedupMapper.insert(fresh);
            return new ClaimResult(ClaimResult.Outcome.NEW, fresh.getId(), null, fresh.getLeaseOwner(), null);
        } catch (DuplicateKeyException e) {
            PetRequestDedup existing = find(userId, endpointKey, requestKey);
            if (existing == null) {
                // 并发同键尚未提交：按处理中返回，重试收敛
                return new ClaimResult(ClaimResult.Outcome.IN_PROGRESS, null, null, null, null);
            }
            if (!existing.getPayloadHash().equals(payloadHash)) {
                // 同键不同内容：409，禁止自动换键（§8.6）
                throw new BusinessException(PetErrorCodes.PET_IDEMPOTENCY_CONFLICT,
                        "请求键已存在但请求内容不同，请确认后使用新键重新发起");
            }
            return switch (existing.getStatus()) {
                case "COMPLETED" -> new ClaimResult(ClaimResult.Outcome.EXISTING, existing.getId(),
                        existing.getResponseJson(), existing.getLeaseOwner(), existing.getBoundPetId());
                case "PROCESSING" -> processingOutcome(existing);
                case "FAILED" -> retry(existing);
                default -> throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                        "请求幂等状态异常: " + existing.getStatus());
            };
        }
    }

    /**
     * P02：PROCESSING 行先看租约——未到期返回处理中（原执行者仍在工作）；
     * 已到期则 CAS 接管成功后按 NEW 重新执行（原执行者视为丢失）。
     */
    private ClaimResult processingOutcome(PetRequestDedup existing) {
        if (tryTakeover(existing.getUserId(), existing.getEndpointKey(), existing.getRequestKey())) {
            // R02 fencing：接管成功后重读行，携带新租约归属——终态回写必须匹配本次接管后的 owner
            PetRequestDedup taken = find(existing.getUserId(), existing.getEndpointKey(), existing.getRequestKey());
            log.info("请求幂等租约到期接管, dedupId={}, previousOwner={}", existing.getId(), existing.getLeaseOwner());
            return new ClaimResult(ClaimResult.Outcome.NEW, existing.getId(), null,
                    taken != null ? taken.getLeaseOwner() : null, existing.getBoundPetId());
        }
        return new ClaimResult(ClaimResult.Outcome.IN_PROGRESS, existing.getId(), null,
                existing.getLeaseOwner(), existing.getBoundPetId());
    }

    @Override
    public boolean tryTakeover(Long userId, String endpointKey, String requestKey) {
        // CAS：仅当仍是 PROCESSING 且租约已到期时轮换租约并推进版本（并发接管只有一个胜者）
        int updated = dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getLeaseOwner, leaseOwner())
                .set(PetRequestDedup::getLeaseUntil, leaseUntil())
                .setSql("version = version + 1")
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey)
                .eq(PetRequestDedup::getStatus, "PROCESSING")
                .lt(PetRequestDedup::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC)));
        return updated == 1;
    }

    /** FAILED 行同键重试：CAS 抢占回 PROCESSING 并续租（并发重试只有一个执行者） */
    private ClaimResult retry(PetRequestDedup existing) {
        int updated = dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getStatus, "PROCESSING")
                .set(PetRequestDedup::getLeaseOwner, leaseOwner())
                .set(PetRequestDedup::getLeaseUntil, leaseUntil())
                .setSql("version = version + 1")
                .eq(PetRequestDedup::getId, existing.getId())
                .eq(PetRequestDedup::getStatus, "FAILED"));
        if (updated == 1) {
            log.info("请求幂等 FAILED 行同键重试, dedupId={}", existing.getId());
            return new ClaimResult(ClaimResult.Outcome.NEW, existing.getId(), null,
                    leaseOwner(), existing.getBoundPetId());
        }
        return new ClaimResult(ClaimResult.Outcome.IN_PROGRESS, existing.getId(), null,
                existing.getLeaseOwner(), existing.getBoundPetId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void completeSucceeded(Long userId, String endpointKey, String requestKey,
                                  String expectedLeaseOwner, Long bizOrderId, String responseJson) {
        int updated = dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getStatus, "COMPLETED")
                .set(PetRequestDedup::getBizOrderId, bizOrderId)
                .set(PetRequestDedup::getResponseJson, responseJson)
                .set(PetRequestDedup::getTerminalErrorCode, null)
                .set(PetRequestDedup::getFinishedAt, LocalDateTime.now(ZoneOffset.UTC))
                .set(PetRequestDedup::getLeaseUntil, null)
                .setSql("version = version + 1")
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey)
                .eq(PetRequestDedup::getStatus, "PROCESSING")
                // R02 fencing：租约上下文存在时必须匹配当前 owner，旧执行者不得覆盖新租约的终态
                .apply(expectedLeaseOwner != null, "lease_owner = {0}", expectedLeaseOwner));
        if (updated != 1) {
            // 0 行：行已被接管重试（本执行者租约失效）。业务事实由 uk 兜底收敛，恢复扫描器按事实对齐终态
            log.warn("请求幂等终态回写未命中（行被并发推进），userId={}, endpointKey={}, requestKey={}",
                    userId, endpointKey, requestKey);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void markFailed(Long userId, String endpointKey, String requestKey,
                           String expectedLeaseOwner, String errorJson) {
        dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getStatus, "FAILED")
                .set(PetRequestDedup::getResponseJson, errorJson)
                .set(PetRequestDedup::getFinishedAt, LocalDateTime.now(ZoneOffset.UTC))
                .set(PetRequestDedup::getLeaseUntil, null)
                .setSql("version = version + 1")
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey)
                .eq(PetRequestDedup::getStatus, "PROCESSING")
                .apply(expectedLeaseOwner != null, "lease_owner = {0}", expectedLeaseOwner));
    }

    @Override
    public void bindPet(Long userId, String endpointKey, String requestKey, Long petId) {
        if (petId == null) {
            return;
        }
        // R02：仅在尚未绑定时写入（幂等）；已绑定行不改归属——同键重试不随主宠切换改投
        dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getBoundPetId, petId)
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey)
                .isNull(PetRequestDedup::getBoundPetId));
    }

    @Override
    public String canonicalHash(Object... parts) {
        StringJoiner joiner = new StringJoiner("|");
        for (Object part : parts) {
            joiner.add(part == null ? "" : String.valueOf(part));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(joiner.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }

    private PetRequestDedup find(Long userId, String endpointKey, String requestKey) {
        return dedupMapper.selectOne(new LambdaQueryWrapper<PetRequestDedup>()
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey));
    }

    private String leaseOwner() {
        return instanceId + ":" + Thread.currentThread().threadId();
    }

    private LocalDateTime leaseUntil() {
        return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(leaseSeconds);
    }
}
