package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.ExchangeCodeFactDTO;
import com.cloudmart.pet.entity.PetFeedEntitlement;
import com.cloudmart.pet.entity.PetLinkedProductOperation;
import com.cloudmart.pet.feign.CouponFeignClient;
import com.cloudmart.pet.repository.PetFeedEntitlementMapper;
import com.cloudmart.pet.repository.PetLinkedProductOperationMapper;
import com.cloudmart.pet.service.PetLinkedProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 实物商品联动（R01/R02）：兑换码权威核验 → 双倍喂食权益。
 *
 * <p>R01：客户端提交的码必须在 mall-coupon 权威核验（存在、已兑换、归属本人）后才发放
 * 权益；操作落可恢复台账——coupon 不可达留 PENDING，按 operationId 重查/后台原单重试；
 * itemCode 由服务端按模板→食物配置推导，客户端不可指定。同一码至多发放一次权益
 * （pet_feed_entitlement uk redemption_code / uk(source_fact_id, unit_sequence)）。</p>
 *
 * <p>R02：一次喂食最多占用一行权益（consumeOneForFeed CAS + feedOperationId 唯一），
 * 占用、背包扣减、宠物数值更新同处 feedItem 事务，失败整体回滚。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetLinkedProductServiceImpl implements PetLinkedProductService {

    private static final int MAX_CODE_LENGTH = 64;

    private final PetFeedEntitlementMapper entitlementMapper;
    private final PetLinkedProductOperationMapper operationMapper;
    private final CouponFeignClient couponFeignClient;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final PetClock petClock;

    @Override
    @Transactional
    public OperationView redeem(Long userId, String redemptionCode, String requestKey) {
        // R19：能力开关——关闭时拒绝新兑换；已发权益与操作进度查询不拦
        if (!properties.getFeatureSwitches().isLinkedProduct()) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "实物权益兑换暂未开放");
        }
        String code = normalizeCode(redemptionCode);
        String key = requestKey == null || requestKey.isBlank()
                ? "r-" + UUID.randomUUID() : requestKey.trim();

        // 幂等一：同请求键重放返回原操作
        PetLinkedProductOperation existing = operationMapper.selectOne(
                new LambdaQueryWrapper<PetLinkedProductOperation>()
                        .eq(PetLinkedProductOperation::getUserId, userId)
                        .eq(PetLinkedProductOperation::getRequestKey, key)
                        .last("LIMIT 1"));
        if (existing != null) {
            return toView(existing);
        }
        // 幂等二：同一码已发放权益（历史操作或恢复路径）→ 直接返回已交付操作
        PetFeedEntitlement granted = entitlementMapper.selectOne(
                new LambdaQueryWrapper<PetFeedEntitlement>()
                        .eq(PetFeedEntitlement::getRedemptionCode, code)
                        .last("LIMIT 1"));
        if (granted != null) {
            return upsertDeliveredOperation(userId, key, code, granted);
        }

        PetLinkedProductOperation operation = new PetLinkedProductOperation();
        operation.setOperationId(UUID.randomUUID().toString());
        operation.setRequestKey(key);
        operation.setUserId(userId);
        operation.setRedemptionCode(code);
        operation.setStatus(PetLinkedProductOperation.STATUS_VERIFYING);
        operation.setVersion(0);
        operationMapper.insert(operation);
        return verifyAndDeliver(operation, userId);
    }

    @Override
    @Transactional
    public OperationView getOperation(Long userId, String operationId) {
        PetLinkedProductOperation operation = operationMapper.selectOne(
                new LambdaQueryWrapper<PetLinkedProductOperation>()
                        .eq(PetLinkedProductOperation::getOperationId, operationId)
                        .last("LIMIT 1"));
        // 他人操作与不存在一律 404（不泄露存在性）
        if (operation == null || !operation.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "核销操作不存在");
        }
        if (PetLinkedProductOperation.STATUS_PENDING.equals(operation.getStatus())
                || PetLinkedProductOperation.STATUS_VERIFYING.equals(operation.getStatus())) {
            // 原单重试一次核验（幂等：已终态操作不会走到这里）
            return verifyAndDeliver(operation, userId);
        }
        return toView(operation);
    }

    @Override
    public EntitlementSummary entitlementSummary(Long userId) {
        List<EntitlementItem> items = listUsable(userId).stream()
                .map(e -> new EntitlementItem(e.getId(), e.getItemCode(), e.getSourceFactId(),
                        e.getStatus(), e.getExpiresAt()))
                .toList();
        return new EntitlementSummary(items.size(), items);
    }

    @Override
    public boolean hasUsableEntitlement(Long userId) {
        return !listUsable(userId).isEmpty();
    }

    @Override
    @Transactional
    public Optional<Long> consumeOneForFeed(Long userId, Long petId, String feedOperationId) {
        // 重放：同一喂食操作键已占用过 → 返回原权益，不二次消耗（uk 兜底并发）
        PetFeedEntitlement replayed = entitlementMapper.selectOne(
                new LambdaQueryWrapper<PetFeedEntitlement>()
                        .eq(PetFeedEntitlement::getConsumedByOperationId, feedOperationId)
                        .last("LIMIT 1"));
        if (replayed != null) {
            return Optional.of(replayed.getId());
        }
        // CAS 占用：选中一条最早已发放的可用权益，按 id+status 条件更新（竞争失败换下一条重试）
        for (int attempt = 0; attempt < 3; attempt++) {
            PetFeedEntitlement candidate = entitlementMapper.selectOne(
                    new LambdaQueryWrapper<PetFeedEntitlement>()
                            .eq(PetFeedEntitlement::getUserId, userId)
                            .eq(PetFeedEntitlement::getStatus, PetFeedEntitlement.STATUS_AVAILABLE)
                            .and(w -> w.isNull(PetFeedEntitlement::getExpiresAt)
                                    .or().gt(PetFeedEntitlement::getExpiresAt, petClock.nowUtc()))
                            .orderByAsc(PetFeedEntitlement::getId)
                            .last("LIMIT 1"));
            if (candidate == null) {
                return Optional.empty();
            }
            int updated = entitlementMapper.update(null, new LambdaUpdateWrapper<PetFeedEntitlement>()
                    .set(PetFeedEntitlement::getStatus, PetFeedEntitlement.STATUS_CONSUMED)
                    .set(PetFeedEntitlement::getConsumedByOperationId, feedOperationId)
                    .set(PetFeedEntitlement::getConsumedPetId, petId)
                    .set(PetFeedEntitlement::getConsumedAt, petClock.nowUtc())
                    .setSql("version = version + 1")
                    .eq(PetFeedEntitlement::getId, candidate.getId())
                    .eq(PetFeedEntitlement::getStatus, PetFeedEntitlement.STATUS_AVAILABLE));
            if (updated == 1) {
                log.info("双倍喂食权益已占用一份: userId={}, petId={}, entitlementId={}, feedOperationId={}",
                        userId, petId, candidate.getId(), feedOperationId);
                return Optional.of(candidate.getId());
            }
            // 0 行：候选权益被并发占用/状态变更，换下一条重试
        }
        return Optional.empty();
    }

    // ---------------- 内部实现 ----------------

    /** 权威核验 + 发放：coupon 事实校验通过才插权益；不可用/异常留 PENDING 可恢复 */
    private OperationView verifyAndDeliver(PetLinkedProductOperation operation, Long userId) {
        try {
            ApiResponse<ExchangeCodeFactDTO> resp =
                    couponFeignClient.getExchangeCodeFact(operation.getRedemptionCode());
            if (resp == null || !resp.success() || resp.data() == null) {
                return markPending(operation, resp == null ? "coupon 空响应" : String.valueOf(resp.error()));
            }
            ExchangeCodeFactDTO fact = resp.data();
            operation.setCouponFactId(fact.codeId());
            operation.setCouponTemplateId(fact.templateId());
            if ("DISABLED".equals(fact.status())) {
                return markRejected(operation, PetErrorCodes.PET_CODE_DISABLED);
            }
            if (!"EXCHANGED".equals(fact.status()) || fact.userId() == null) {
                // 未兑换/已回滚：权益前提不成立（码必须先在 coupon 侧完成本人兑换）
                return markRejected(operation, PetErrorCodes.PET_CODE_NOT_EXCHANGED);
            }
            if (!userId.equals(fact.userId())) {
                return markRejected(operation, PetErrorCodes.PET_CODE_NOT_OWNED);
            }
            Long entitlementId = grantEntitlement(userId, operation.getRedemptionCode(), fact);
            operation.setEntitlementId(entitlementId);
            operation.setStatus(PetLinkedProductOperation.STATUS_DELIVERED);
            operation.setRejectReasonCode(null);
            operation.setLastError(null);
            operationMapper.updateById(operation);
            log.info("商品权益核销交付: userId={}, code={}, entitlementId={}, operationId={}",
                    userId, operation.getRedemptionCode(), entitlementId, operation.getOperationId());
            return toView(operation);
        } catch (BusinessException e) {
            if ("EXCHANGE_CODE_NOT_FOUND".equals(e.getCode())) {
                return markRejected(operation, PetErrorCodes.PET_CODE_NOT_FOUND);
            }
            // coupon 拒绝/限流等业务异常：可重试，留 PENDING
            return markPending(operation, e.getMessage());
        } catch (Exception e) {
            log.warn("权益核验异常，操作转 PENDING: operationId={}", operation.getOperationId(), e);
            return markPending(operation, e.getMessage());
        }
    }

    /** 发放一条权益（一份一行；uk(source_fact_id, unit_sequence)/uk(redemption_code) 幂等） */
    private Long grantEntitlement(Long userId, String code, ExchangeCodeFactDTO fact) {
        PetFeedEntitlement entitlement = new PetFeedEntitlement();
        entitlement.setUserId(userId);
        entitlement.setRedemptionCode(code);
        entitlement.setSourceFactId(fact.codeId());
        entitlement.setUnitSequence(1);
        entitlement.setStatus(PetFeedEntitlement.STATUS_AVAILABLE);
        entitlement.setItemCode(deriveItemCode(fact.templateId()));
        try {
            entitlementMapper.insert(entitlement);
            return entitlement.getId();
        } catch (DuplicateKeyException duplicate) {
            // 并发同码：唯一约束胜者已发放，返回已存在权益（同码只发一次）
            PetFeedEntitlement existing = entitlementMapper.selectOne(
                    new LambdaQueryWrapper<PetFeedEntitlement>()
                            .eq(PetFeedEntitlement::getRedemptionCode, code)
                            .last("LIMIT 1"));
            log.info("并发同码核销命中已发放权益: userId={}, code={}, entitlementId={}",
                    userId, code, existing == null ? null : existing.getId());
            return existing == null ? null : existing.getId();
        }
    }

    /** itemCode 服务端推导（R01）：模板→食物映射，未配置发通用权益 */
    private String deriveItemCode(Long templateId) {
        if (templateId == null) {
            return "ANY";
        }
        return properties.getLinkedProduct().getTemplateItems()
                .getOrDefault(String.valueOf(templateId), "ANY");
    }

    /** 同码已有权益时的恢复路径：补一条 DELIVERED 操作并关联（无则创建） */
    private OperationView upsertDeliveredOperation(Long userId, String requestKey,
                                                   String code, PetFeedEntitlement granted) {
        PetLinkedProductOperation operation = operationMapper.selectOne(
                new LambdaQueryWrapper<PetLinkedProductOperation>()
                        .eq(PetLinkedProductOperation::getRedemptionCode, code)
                        .eq(PetLinkedProductOperation::getStatus, PetLinkedProductOperation.STATUS_DELIVERED)
                        .last("LIMIT 1"));
        if (operation == null) {
            operation = new PetLinkedProductOperation();
            operation.setOperationId(UUID.randomUUID().toString());
            operation.setRequestKey(requestKey);
            operation.setUserId(userId);
            operation.setRedemptionCode(code);
            operation.setCouponFactId(granted.getSourceFactId());
            operation.setEntitlementId(granted.getId());
            operation.setStatus(PetLinkedProductOperation.STATUS_DELIVERED);
            operation.setVersion(0);
            operationMapper.insert(operation);
        }
        return toView(operation);
    }

    private OperationView markRejected(PetLinkedProductOperation operation, String reasonCode) {
        operation.setStatus(PetLinkedProductOperation.STATUS_REJECTED);
        operation.setRejectReasonCode(reasonCode);
        operationMapper.updateById(operation);
        log.info("权益核销拒绝: operationId={}, code={}, reason={}",
                operation.getOperationId(), operation.getRedemptionCode(), reasonCode);
        return toView(operation);
    }

    private OperationView markPending(PetLinkedProductOperation operation, String error) {
        operation.setStatus(PetLinkedProductOperation.STATUS_PENDING);
        operation.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        operationMapper.updateById(operation);
        log.warn("权益核验暂挂（可恢复）: operationId={}, code={}, error={}",
                operation.getOperationId(), operation.getRedemptionCode(), error);
        return toView(operation);
    }

    private List<PetFeedEntitlement> listUsable(Long userId) {
        return entitlementMapper.selectList(new LambdaQueryWrapper<PetFeedEntitlement>()
                .eq(PetFeedEntitlement::getUserId, userId)
                .eq(PetFeedEntitlement::getStatus, PetFeedEntitlement.STATUS_AVAILABLE)
                .and(w -> w.isNull(PetFeedEntitlement::getExpiresAt)
                        .or().gt(PetFeedEntitlement::getExpiresAt, petClock.nowUtc()))
                .orderByAsc(PetFeedEntitlement::getId));
    }

    private OperationView toView(PetLinkedProductOperation operation) {
        return new OperationView(operation.getOperationId(), operation.getStatus(),
                operation.getEntitlementId(), operation.getRejectReasonCode(),
                PetLinkedProductOperation.STATUS_DELIVERED.equals(operation.getStatus()));
    }

    private String normalizeCode(String redemptionCode) {
        if (redemptionCode == null || redemptionCode.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "兑换码不能为空");
        }
        String code = redemptionCode.trim().toUpperCase();
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "兑换码格式非法");
        }
        return code;
    }
}
