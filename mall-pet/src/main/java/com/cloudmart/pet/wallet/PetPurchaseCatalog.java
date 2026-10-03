package com.cloudmart.pet.wallet;

import java.util.List;

/**
 * 购买目录 SPI（W01）：商品/技能/物种校验与服务端权威价格快照。
 *
 * <p>W02 由商城目录实现并注入；应用服务不接受客户端金额（§5.3）。
 * 价格版本变更时 expectedConfigVersion 不匹配抛 {@code PET_CONFIG_VERSION_CONFLICT}（409），
 * 禁止静默按更贵价格扣款。</p>
 */
public interface PetPurchaseCatalog {

    /**
     * 加载商品快照并校验可售（物种/前置条件/上下架）。
     *
     * @throws com.cloudmart.common.exception.BusinessException 商品不存在 / 不可购买 / 版本冲突
     */
    CatalogEntry load(Long userId, Long petId, String itemType, String itemCode, String expectedConfigVersion);

    /**
     * @return 该物品类型是否同一宠物仅可拥有一份（R02：唯一性按 petId+类型+编码，
     *         不改成全账号唯一——多宠各自拥有；FOOD 堆叠可重复购买）
     */
    boolean isUniquePerPet(String itemType);

    /** @return 物品是否已归属该宠物（唯一类物品的资格锁判定，T06） */
    boolean isOwnedByPet(Long petId, String itemType, String itemCode);

    /** 商品快照（服务端权威） */
    record CatalogEntry(
            String itemType,
            String itemCode,
            long unitPrice,
            String configVersion,
            String displayName,
            String resourceKey
    ) {
    }

    /** 不可重复物品已拥有异常（统一错误码，禁止继续扣款） */
    class AlreadyOwnedException extends RuntimeException {
        public AlreadyOwnedException(String message) {
            super(message);
        }
    }

    /** 资产交付回调（W02 注册；在业务事务内执行，失败整体回滚） */
    interface PetAssetDeliverer {

        /** @return 是否受理该物品类型的交付（R02：每种类型必须恰好匹配一个交付器，否则整单失败） */
        boolean supports(String itemType);

        /**
         * 交付资产到业务库存（装备/皮肤/技能/家具/食物等）。
         *
         * @return 实际交付的 reward_slot 列表（空列表=无业务库存，仅记录发放事实）
         */
        List<String> deliver(DeliveryContext context);

        record DeliveryContext(
                Long userId,
                Long petId,
                Long orderId,
                String itemType,
                String itemCode,
                int quantity,
                CatalogEntry entry
        ) {
        }
    }
}
