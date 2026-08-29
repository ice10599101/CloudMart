package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.Brand;
import com.cloudmart.wish.entity.BrandPool;
import com.cloudmart.wish.entity.UserAsset;
import com.cloudmart.wish.entity.VirtualAsset;

import java.util.List;
import java.util.Map;

/**
 * 虚拟收藏 + 品牌合作服务（Sprint 3.6，文档 2.22/2.16/3.6）。
 *
 * <p>RMB 内购偏差：对接 mall-payment 的订单创建/回调/关单依赖支付通道
 * 联调，当前 exchange(paymentMethod=RMB) 返回 400 WISH_VALIDATION_ERROR
 * （"RMB 内购暂未开通"）——偏差留档进度文件，星光兑换闭环完整。</p>
 */
public interface CollectionService {

    /** 虚拟工坊资产列表（公开；上架中 + 已拥有标记） */
    List<Map<String, Object>> workshopAssets(Long userId);

    /**
     * 星光兑换（幂等：重复兑换抛 WISH_ALREADY_OWNED；限量 Redis DECR
     * 预扣；余额不足 402）。RMB 通道偏差留档，当前拒绝。
     */
    UserAsset exchange(Long userId, Long assetId, String paymentMethod);

    /**
     * 收藏馆（按类型分组：BADGE 来自 wish_user_badge / SKIN/BGM/
     * SPECIAL_FRUIT 来自 wish_user_asset）。
     */
    Map<String, List<Map<String, Object>>> collections(Long userId);

    /** 星火收藏品：收藏 SPARK 心愿为 SPECIAL_FRUIT 资产（幂等） */
    UserAsset collectSpark(Long userId, Long wishId);

    /** 皮肤/BGM 切换（同类型互斥，即时生效） */
    void setActiveAsset(Long userId, Long assetId);

    // ---------------- 管理端：资产 CRUD / 品牌审核 ----------------

    /** 资产全量列表（含下架） */
    List<VirtualAsset> listAllAssets();

    /** 资产 upsert（配置表化：新增皮肤仅插入配置行） */
    VirtualAsset saveAsset(VirtualAsset asset);

    /** 资产上/下架 */
    void toggleAsset(Long assetId, boolean active);

    /** 品牌入驻审核 */
    void auditBrand(Long brandId, String status);

    // ---------------- 品牌许愿池 ----------------

    List<Brand> listBrands();

    List<BrandPool> listPools(Long brandId);

    BrandPool createPool(BrandPool pool, Long adminUserId);

    void joinPool(Long userId, Long poolId);
}
