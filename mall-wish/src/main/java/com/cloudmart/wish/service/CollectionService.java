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
    /** 单资产详情（文档 2.22；上架中或已拥有可见，含 obtainMethod） */
    Map<String, Object> assetDetail(Long assetId, Long userId);


    /**
     * 星光兑换（幂等：重复兑换抛 WISH_ALREADY_OWNED；限量 Redis DECR
     * 预扣；余额不足 402）。RMB 通道偏差留档，当前拒绝。
     */
    /**
     * 星光兑换资产（B04/B05）：持久幂等 + DB 条件扣库存 + 原子扣款，
     * 返回扣款后余额；余额不足由钱包统一抛 402。
     *
     * @param idempotencyKey 客户端幂等键（X-Idempotency-Key；可空则按单次请求处理）
     */
    com.cloudmart.wish.vo.ExchangeResultVO exchange(Long userId, Long assetId,
                                                    String paymentMethod, String idempotencyKey);

    /**
     * 收藏馆（按类型分组：BADGE 来自 wish_user_badge / SKIN/BGM/
     * SPECIAL_FRUIT 来自 wish_user_asset）。
     */
    Map<String, List<Map<String, Object>>> collections(Long userId);

    /**
     * 收藏馆分组视图（可按类型过滤）。
     *
     * @param type 可选：BADGE/SKIN/BGM/SPECIAL_FRUIT；空返回全部分组
     */
    Map<String, List<Map<String, Object>>> collections(Long userId, String type);

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

    /**
     * 删除资产（配置行）。
     * 安全约束：任一用户持有该资产（wish_user_asset 存在记录）时拒绝删除，
     * 提示改用下架，避免用户已拥有的资产变成孤儿数据。
     */
    void deleteAsset(Long assetId);

    /** 品牌入驻审核 */
    void auditBrand(Long brandId, String status);

    // ---------------- 品牌许愿池 ----------------

    List<Brand> listBrands();

    /** 管理端：全状态品牌列表（含 PENDING/REJECTED，入驻审核数据源） */
    List<Brand> listAllBrands();

    List<BrandPool> listPools(Long brandId);

    BrandPool createPool(BrandPool pool, Long adminUserId);
    /** 池更新（管理端；WISH-03：仅 DRAFT 可改——ACTIVE 后规则冻结） */
    BrandPool updatePool(Long poolId, BrandPool patch, Long adminUserId);

    /** WISH-03：发布（DRAFT→ACTIVE）——品牌必须已批准，冻结 ruleVersion */
    BrandPool publishPool(Long poolId, Long adminUserId);

    /** WISH-03：CONFIG_ERROR 池修复后的审计重试 */
    Map<String, Object> retrySettlement(Long poolId, Long adminUserId);

    /** 池终止（管理端；ACTIVE → ENDED，不再接受加入） */
    void endPool(Long poolId);

    /** 达标发奖（文档 9.3 wish-brand-reward-check：current>=target 且 ACTIVE → 发奖 + ENDED；幂等） */
    Map<String, Object> brandRewardCheck();

    void joinPool(Long userId, Long poolId);

    /**
     * T11：按 path.brandId 加入——池必须属于该品牌，错误关联按 404 处理（不泄露其他品牌池）。
     */
    void joinPool(Long userId, Long brandId, Long poolId);

    /** 许愿池详情（含当前进度） */
    BrandPool getPoolDetail(Long poolId);

    /** 许愿池奖励记录（脱敏，暂返回空——品牌奖励发放后续迭代） */
    List<Map<String, Object>> poolRewards(Long poolId);

    /** WISH-03：我的参加记录（本人维度：参加的池 + 进度） */
    List<Map<String, Object>> myPoolJoins(Long userId);

    /** WISH-03：我的奖励结果（本人维度：义务状态/承诺/实际入账） */
    List<Map<String, Object>> myPoolRewards(Long userId);

    /** 品牌数据访问审计日志（暂返回空——审计埋点后续迭代） */
    List<Map<String, Object>> brandAuditLogs(Long brandId);
}
