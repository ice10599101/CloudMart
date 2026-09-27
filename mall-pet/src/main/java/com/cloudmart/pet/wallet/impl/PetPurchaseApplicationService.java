package com.cloudmart.pet.wallet.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetAssetGrant;
import com.cloudmart.pet.entity.PetPurchaseOrder;
import com.cloudmart.pet.repository.PetAssetGrantMapper;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import com.cloudmart.pet.wallet.PetPurchaseCatalog.PetAssetDeliverer;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * 宠物币购买应用服务（W01/§5.3 购买事务顺序）。
 *
 * <p>编排（非事务方法）：</p>
 * <ol>
 *   <li>幂等键格式校验 + 规范 payload 摘要 → dedup.claim（REQUIRES_NEW 占键）；</li>
 *   <li>业务事务（TransactionTemplate）：目录校验/价格快照 → 建订单 → 钱包扣款 →
 *       资产交付 + asset_grant → 订单完成（T04/T06/T08）；</li>
 *   <li>业务事务提交后 dedup 终态落库（REQUIRES_NEW）——成功与业务拒绝均保存终态响应，
 *       未知失败置 FAILED（同键可重试，T07）。</li>
 * </ol>
 *
 * <p>不可重复物品：第二个不同请求键在目录资格判定发现已拥有 → ALREADY_OWNED 零扣款
 * （终态响应），同请求键返回原成功结果（T06）。</p>
 */
@Service
@Slf4j
public class PetPurchaseApplicationService {

    private static final String ENDPOINT_KEY = "PURCHASE";

    private final PetRequestDedupService dedupService;
    private final PetWalletService walletService;
    /** 目录实现随 W-02 商城接入提供；ObjectProvider 允许无实现时服务正常启动（购买入口不可用） */
    private final org.springframework.beans.factory.ObjectProvider<PetPurchaseCatalog> catalogProvider;
    private final ObjectProvider<PetAssetDeliverer> deliverers;
    private final PetPurchaseOrderMapper orderMapper;
    private final PetAssetGrantMapper assetGrantMapper;
    private final TransactionTemplate transactionTemplate;

    public PetPurchaseApplicationService(PetRequestDedupService dedupService,
                                         PetWalletService walletService,
                                         ObjectProvider<PetPurchaseCatalog> catalogProvider,
                                         ObjectProvider<PetAssetDeliverer> deliverers,
                                         PetPurchaseOrderMapper orderMapper,
                                         PetAssetGrantMapper assetGrantMapper,
                                         TransactionTemplate transactionTemplate) {
        this.dedupService = dedupService;
        this.walletService = walletService;
        this.catalogProvider = catalogProvider;
        this.deliverers = deliverers;
        this.orderMapper = orderMapper;
        this.assetGrantMapper = assetGrantMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 幂等购买。
     *
     * @param requestKey 客户端持久化幂等键（16..128 ASCII；同一意图重试必须复用）
     */
    public PurchaseResult purchase(Long userId, Long petId, String itemType, String itemCode,
                                   String requestKey, String expectedConfigVersion) {
        if (!PetRequestDedupService.isValidRequestKey(requestKey)) {
            throw new BusinessException(PetErrorCodes.PET_REQUEST_KEY_INVALID,
                    "缺少有效幂等键（16..128 ASCII），请重试一次由客户端生成");
        }
        String payloadHash = dedupService.canonicalHash(
                userId, petId == null ? "" : petId, itemType, itemCode, expectedConfigVersion == null ? "" : expectedConfigVersion);

        PetRequestDedupService.ClaimResult claim = dedupService.claim(
                userId, ENDPOINT_KEY, requestKey, payloadHash);
        switch (claim.outcome()) {
            case EXISTING -> {
                // 同请求键：返回原终态结果（成功或业务拒绝），不重新算价（T05/T07）
                PurchaseResult stored = PetJsonUtils.parse(claim.responseJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<PurchaseResult>() {
                        });
                if (stored != null) {
                    return new PurchaseResult(stored.orderId(), stored.operationId(), stored.walletTransactionId(),
                            stored.balanceAfter(), stored.itemType(), stored.itemCode(), stored.deliveredSlots(),
                            true, stored.errorCode());
                }
                throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "历史购买结果快照损坏");
            }
            case IN_PROGRESS -> throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS,
                    "购买请求处理中，请稍后按原请求查询结果");
            case NEW -> {
                // 继续执行
            }
        }

        try {
            PurchaseResult result = transactionTemplate.execute(status -> doPurchase(
                    userId, petId, itemType, itemCode, expectedConfigVersion, requestKey));
            dedupService.completeSucceeded(userId, ENDPOINT_KEY, requestKey,
                    result.orderId() == null ? null : Long.valueOf(result.orderId()),
                    PetJsonUtils.toJson(result));
            return result;
        } catch (PetPurchaseCatalog.AlreadyOwnedException already) {
            // 不可重复物品已拥有：零扣款的明确拒绝，终态响应保存（T06）
            PurchaseResult rejected = new PurchaseResult(null, operationIdOf(userId, requestKey), null,
                    null, itemType, itemCode, List.of(), false, PetErrorCodes.PET_ITEM_ALREADY_OWNED);
            dedupService.completeSucceeded(userId, ENDPOINT_KEY, requestKey, null, PetJsonUtils.toJson(rejected));
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "已拥有该物品，无需重复购买");
        } catch (BusinessException definite) {
            // 业务明确拒绝（余额不足/版本冲突/不可购买）：保存终态响应，同键返回原拒绝（§8.2）
            PurchaseResult rejected = new PurchaseResult(null, operationIdOf(userId, requestKey), null,
                    null, itemType, itemCode, List.of(), false, definite.getCode());
            dedupService.completeSucceeded(userId, ENDPOINT_KEY, requestKey, null, PetJsonUtils.toJson(rejected));
            throw definite;
        } catch (RuntimeException unknown) {
            // 非业务异常（基础设施等）：置 FAILED，同键可安全重试
            log.error("购买事务未知失败, userId={}, itemType={}, itemCode={}", userId, itemType, itemCode, unknown);
            dedupService.markFailed(userId, ENDPOINT_KEY, requestKey,
                    PetJsonUtils.toJson(Map.of("error", String.valueOf(unknown.getMessage()))));
            throw unknown;
        }
    }

    /** 业务事务（T08：扣币后任何一步失败整体回滚） */
    private PurchaseResult doPurchase(Long userId, Long petId, String itemType, String itemCode,
                                      String expectedConfigVersion, String requestKey) {
        PetPurchaseCatalog catalog = catalogProvider.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException("PET_TEMPORARILY_UNAVAILABLE", "商城目录未接入，购买暂不可用");
        }
        PetPurchaseCatalog.CatalogEntry entry = catalog.load(userId, petId, itemType, itemCode, expectedConfigVersion);
        if (catalog.isUniquePerUser(itemType) && catalog.isOwnedByUser(userId, itemType, itemCode)) {
            throw new PetPurchaseCatalog.AlreadyOwnedException(itemType + ":" + itemCode);
        }

        long totalAmount = entry.unitPrice();
        String operationId = operationIdOf(userId, requestKey);
        String requestHash = dedupService.canonicalHash(ENDPOINT_KEY, requestKey, itemType, itemCode);

        PetPurchaseOrder order = new PetPurchaseOrder();
        order.setUserId(userId);
        order.setPetId(petId);
        order.setItemType(itemType);
        order.setItemCode(itemCode);
        order.setQuantity(1);
        order.setUnitPrice(entry.unitPrice());
        order.setTotalAmount(totalAmount);
        order.setCurrency("PET_COIN");
        order.setWalletDomain("PET");
        order.setConfigVersion(entry.configVersion());
        order.setItemSnapshot(PetJsonUtils.toJson(Map.of(
                "itemType", itemType, "itemCode", itemCode,
                "displayName", entry.displayName() == null ? "" : entry.displayName(),
                "resourceKey", entry.resourceKey() == null ? "" : entry.resourceKey(),
                "unitPrice", entry.unitPrice())));
        order.setStatus("PROCESSING");
        orderMapper.insert(order);

        // 零价物品：不产生流水，直接发放（免费工作/新手赠品类不在此入口）
        PetWalletResult wallet;
        if (totalAmount > 0) {
            wallet = walletService.debit(new PetWalletCommand(userId, petId, "SPEND",
                    "PURCHASE", String.valueOf(order.getId()), totalAmount, operationId, requestHash,
                    null, entry.configVersion(), null));
        } else {
            wallet = new PetWalletResult(null, operationId, currentBalance(userId), 0, "COMMITTED", false);
        }

        List<String> slots = deliver(order, userId, petId, entry);

        order.setStatus("COMPLETED");
        order.setWalletTransactionId(wallet.transactionId());
        orderMapper.updateById(order);

        return new PurchaseResult(String.valueOf(order.getId()), operationId,
                wallet.transactionId() == null ? null : String.valueOf(wallet.transactionId()),
                wallet.balanceAfter(), itemType, itemCode, slots, false, null);
    }

    /** 资产交付：asset_grant 事实记录（uk 保证同单同槽一次）+ 交付器回调业务库存 */
    private List<String> deliver(PetPurchaseOrder order, Long userId, Long petId,
                                 PetPurchaseCatalog.CatalogEntry entry) {
        PetAssetGrant grant = new PetAssetGrant();
        grant.setUserId(userId);
        grant.setPetId(petId);
        grant.setSourceType("ORDER");
        grant.setSourceId(order.getId());
        grant.setRewardSlot(entry.itemCode());
        grant.setItemType(order.getItemType());
        grant.setItemCode(order.getItemCode());
        grant.setQuantity(order.getQuantity());
        try {
            assetGrantMapper.insert(grant);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 同单同槽已发放（重复回调）：幂等跳过
            return List.of(entry.itemCode());
        }
        List<String> slots = List.of(entry.itemCode());
        for (PetAssetDeliverer deliverer : deliverers) {
            List<String> delivered = deliverer.deliver(new PetAssetDeliverer.DeliveryContext(
                    userId, petId, order.getId(), order.getItemType(), order.getItemCode(),
                    order.getQuantity(), entry));
            if (delivered != null && !delivered.isEmpty()) {
                slots = delivered;
            }
        }
        return slots;
    }

    private long currentBalance(Long userId) {
        return walletService.getOrCreateAccount(userId).getBalance();
    }

    /** 操作键：pw_ + 64 位摘要（服务器固定格式，§5.3，最长 67 字符；同请求键重试得到同一键） */
    private String operationIdOf(Long userId, String requestKey) {
        return "pw_" + dedupService.canonicalHash("PURCHASE|" + userId + "|" + requestKey);
    }

    /**
     * 购买结果（契约 §8.1：ID/操作键为字符串，余额为十进制字符串语义由序列化层保证）。
     *
     * @param duplicate 是否命中原请求终态
     * @param errorCode 业务拒绝码（成功为 null）
     */
    public record PurchaseResult(
            String orderId,
            String operationId,
            String walletTransactionId,
            Long balanceAfter,
            String itemType,
            String itemCode,
            List<String> deliveredSlots,
            boolean duplicate,
            String errorCode
    ) {
    }
}
