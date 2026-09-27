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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

/**
 * 请求级幂等去重实现（W01）。
 *
 * <p>claim 以 REQUIRES_NEW 先行提交 PROCESSING 行——请求键必须先于业务事务占住，
 * 调用方回滚不能连带抹掉（否则重试丢失收敛锚点）。终态回写同为独立小事务，
 * 在业务事务提交之后调用。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetRequestDedupServiceImpl implements PetRequestDedupService {

    private final PetRequestDedupMapper dedupMapper;

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
        try {
            dedupMapper.insert(fresh);
            return new ClaimResult(ClaimResult.Outcome.NEW, fresh.getId(), null);
        } catch (DuplicateKeyException e) {
            PetRequestDedup existing = find(userId, endpointKey, requestKey);
            if (existing == null) {
                // 并发同键尚未提交：按处理中返回，重试收敛
                return new ClaimResult(ClaimResult.Outcome.IN_PROGRESS, null, null);
            }
            if (!existing.getPayloadHash().equals(payloadHash)) {
                // 同键不同内容：409，禁止自动换键（§8.6）
                throw new BusinessException(PetErrorCodes.PET_IDEMPOTENCY_CONFLICT,
                        "请求键已存在但请求内容不同，请确认后使用新键重新发起");
            }
            return switch (existing.getStatus()) {
                case "COMPLETED" -> new ClaimResult(ClaimResult.Outcome.EXISTING, existing.getId(),
                        existing.getResponseJson());
                case "PROCESSING" -> new ClaimResult(ClaimResult.Outcome.IN_PROGRESS, existing.getId(), null);
                case "FAILED" -> retry(existing, payloadHash);
                default -> throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                        "请求幂等状态异常: " + existing.getStatus());
            };
        }
    }

    /** FAILED 行同键重试：CAS 抢占回 PROCESSING（并发重试只有一个执行者） */
    private ClaimResult retry(PetRequestDedup existing, String payloadHash) {
        int updated = dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getStatus, "PROCESSING")
                .eq(PetRequestDedup::getId, existing.getId())
                .eq(PetRequestDedup::getStatus, "FAILED"));
        if (updated == 1) {
            log.info("请求幂等 FAILED 行同键重试, dedupId={}", existing.getId());
            return new ClaimResult(ClaimResult.Outcome.NEW, existing.getId(), null);
        }
        return new ClaimResult(ClaimResult.Outcome.IN_PROGRESS, existing.getId(), null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void completeSucceeded(Long userId, String endpointKey, String requestKey,
                                  Long bizOrderId, String responseJson) {
        int updated = dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getStatus, "COMPLETED")
                .set(PetRequestDedup::getBizOrderId, bizOrderId)
                .set(PetRequestDedup::getResponseJson, responseJson)
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey));
        if (updated != 1) {
            log.warn("请求幂等终态回写未命中（行被并发推进），userId={}, endpointKey={}, requestKey={}",
                    userId, endpointKey, requestKey);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(Long userId, String endpointKey, String requestKey, String errorJson) {
        dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getStatus, "FAILED")
                .set(PetRequestDedup::getResponseJson, errorJson)
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, endpointKey)
                .eq(PetRequestDedup::getRequestKey, requestKey));
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
}
