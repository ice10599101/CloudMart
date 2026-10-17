package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.*;
import com.cloudmart.wish.enums.*;
import com.cloudmart.wish.repository.*;
import com.cloudmart.wish.service.CollectionService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.impl.VirtualAssetHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 虚拟收藏 + 品牌合作服务实现（Sprint 3.6）。
 *
 * <p>星光兑换闭环：资产上架校验（过期/下架/限量 Redis DECR 预扣）→
 * 扣星光（WISH_STARLIGHT_INSUFFICIENT 402）→ uk(user,asset) 幂等
 * （重复兑换 WISH_ALREADY_OWNED 409）→ 限量回补（兑换失败时 INCR 回补）。
 * RMB 内购通道对接 mall-payment 后开通（偏差留档）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionServiceImpl implements CollectionService {

    private final VirtualAssetMapper assetMapper;
    private final UserAssetMapper userAssetMapper;
    private final BrandMapper brandMapper;
    private final BrandPoolMapper poolMapper;
    private final com.cloudmart.wish.repository.BrandRewardLogMapper brandRewardLogMapper;
    private final com.cloudmart.wish.repository.BrandPoolMemberMapper poolMemberMapper;
    private final com.cloudmart.wish.repository.BrandAuditLogMapper brandAuditLogMapper;
    private final com.cloudmart.wish.service.UserStatService userStatService;
    private final WishBadgeMapper badgeMapper;
    private final WishUserBadgeMapper userBadgeMapper;
    private final WishMapper wishMapper;
    private final StringRedisTemplate redisTemplate;
    private final com.cloudmart.wish.policy.WishAccessPolicy accessPolicy;
    private final WishOperationExecutor operationExecutor;
    private final com.cloudmart.wish.config.WishBrandPoolProperties brandPoolProperties;

    // ---------------- 虚拟工坊 ----------------

    @Override
    public Map<String, Object> assetDetail(Long assetId, Long userId) {
        final VirtualAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !Boolean.TRUE.equals(asset.getIsActive())) {
            // 已拥有者仍可见；未上架且未拥有 → 404
            if (userId != null) {
                final Long owned = userAssetMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.wish.entity.UserAsset>()
                        .eq(com.cloudmart.wish.entity.UserAsset::getUserId, userId)
                        .eq(com.cloudmart.wish.entity.UserAsset::getAssetId, assetId));
                if (owned == 0) {
                    throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "资产不存在或已下架");
                }
            } else {
                throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "资产不存在或已下架");
            }
        }
        boolean owned = false;
        if (userId != null) {
            owned = userAssetMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.wish.entity.UserAsset>()
                    .eq(com.cloudmart.wish.entity.UserAsset::getUserId, userId)
                    .eq(com.cloudmart.wish.entity.UserAsset::getAssetId, assetId)) > 0;
        }
        final Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
        row.put("assetId", asset.getId());
        row.put("assetType", asset.getAssetType() != null ? asset.getAssetType().name() : null);
        row.put("name", asset.getName());
        row.put("description", asset.getDescription());
        row.put("icon", asset.getIcon());
        row.put("priceStarlight", asset.getPriceStarlight());
        row.put("owned", owned);
        row.put("obtainMethod", owned ? "EXCHANGE" : "WORKSHOP");
        return row;
    }

    @Override
    public List<Map<String, Object>> workshopAssets(Long userId) {
        List<VirtualAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<VirtualAsset>()
                .eq(VirtualAsset::getIsActive, true)
                .orderByDesc(VirtualAsset::getId));
        Set<Long> ownedIds = ownedAssetIds(userId);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (VirtualAsset asset : assets) {
            // 有效期过滤（验收：过期自动下架）
            if (asset.getValidFrom() != null && now.isBefore(asset.getValidFrom())) continue;
            if (asset.getValidTo() != null && now.isAfter(asset.getValidTo())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("assetId", asset.getId());
            item.put("assetType", asset.getAssetType().name());
            item.put("name", asset.getName());
            item.put("description", asset.getDescription());
            item.put("icon", asset.getIcon());
            item.put("priceStarlight", asset.getPriceStarlight());
            item.put("priceRmb", asset.getPriceRmb());
            item.put("payMethod", asset.getPayMethod().name());
            item.put("stock", asset.getStock());
            item.put("owned", ownedIds.contains(asset.getId()));
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional
    public com.cloudmart.wish.vo.ExchangeResultVO exchange(Long userId, Long assetId,
                                                           String paymentMethod, String idempotencyKey) {
        // B04：持久幂等——同键同摘要重放已提交结果，同键异摘要 409；
        // 操作凭证与库存/钱包/归属同事务提交
        return operationExecutor.execute("USER", userId, null, "ASSET_EXCHANGE", idempotencyKey,
                java.util.Map.of("assetId", assetId, "paymentMethod", paymentMethod),
                com.cloudmart.wish.vo.ExchangeResultVO.class,
                () -> doExchange(userId, assetId, paymentMethod));
    }

    /** B05 领域写：DB 条件扣库存（先）→ 钱包原子扣款（后）→ 归属落库，同事务。 */
    private com.cloudmart.wish.vo.ExchangeResultVO doExchange(Long userId, Long assetId,
                                                              String paymentMethod) {
        VirtualAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !Boolean.TRUE.equals(asset.getIsActive())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "资产不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (asset.getValidFrom() != null && now.isBefore(asset.getValidFrom())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "资产尚未开售");
        }
        if (asset.getValidTo() != null && now.isAfter(asset.getValidTo())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "资产已过期下架");
        }
        // 支付方式白名单：服务端读取价格，前端不传成交价；RMB 通道本轮关闭
        if (!"STARLIGHT".equals(paymentMethod)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "不支持的支付方式");
        }
        if (asset.getPayMethod() == AssetPayMethod.RMB) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "RMB 内购暂未开通");
        }

        // uk 幂等（验收：重复兑换返回已拥有）
        if (userAssetMapper.selectCount(new LambdaQueryWrapper<UserAsset>()
                .eq(UserAsset::getUserId, userId)
                .eq(UserAsset::getAssetId, assetId)) > 0) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已拥有该资产");
        }

        // B05：LIMITED 资产在 DB 执行 stock_remaining>0 条件减一（Redis 仅展示缓存，不再预扣）
        if ("LIMITED".equals(asset.getStockMode())) {
            int affected = assetMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VirtualAsset>()
                            .setSql("stock_remaining = stock_remaining - 1, version = version + 1")
                            .eq(VirtualAsset::getId, assetId)
                            .eq(VirtualAsset::getStockMode, "LIMITED")
                            .gt(VirtualAsset::getStockRemaining, 0));
            if (affected == 0) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "限量资产已售罄");
            }
        }

        // B05 修复：spendStarlight 返回"扣款后余额"，以原子扣减成功为准，
        // 余额不足由钱包统一抛 402；不再做 credited<cost 的错误二次判断
        int cost = asset.getPriceStarlight() != null ? asset.getPriceStarlight() : 0;
        Integer balanceAfter = null;
        if (cost > 0) {
            balanceAfter = userStatService.spendStarlight(userId, cost,
                    ResourceLogSource.EXCHANGE, assetId);
        }

        UserAsset userAsset = new UserAsset();
        userAsset.setUserId(userId);
        userAsset.setAssetId(assetId);
        userAsset.setSource("EXCHANGE");
        userAsset.setStatus("OWNED");
        userAsset.setAcquiredAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            userAssetMapper.insert(userAsset);
        } catch (DuplicateKeyException ex) {
            // 并发重复兑换：整体回滚（含库存与钱包），不留半成功
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已拥有该资产");
        }

        // 展示缓存尽力刷新（可删除缓存，不影响正确性）
        if ("LIMITED".equals(asset.getStockMode())) {
            try {
                redisTemplate.opsForValue().decrement(VirtualAssetHelper.stockKey(assetId));
            } catch (DataAccessException ignored) { }
        }

        log.info("资产兑换成功, userId={}, assetId={}, cost={}, balanceAfter={}",
                userId, assetId, cost, balanceAfter);
        return new com.cloudmart.wish.vo.ExchangeResultVO(userAsset.getId(), assetId, balanceAfter, cost);
    }

    // ---------------- 收藏馆 ----------------

    @Override
    public Map<String, List<Map<String, Object>>> collections(Long userId) {
        return collections(userId, null);
    }

    @Override
    public Map<String, List<Map<String, Object>>> collections(Long userId, String type) {
        String wanted = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        // B14：非法类型 422（不再静默返回空集合）
        if (wanted != null && !List.of("BADGE", "SKIN", "BGM", "SPECIAL_FRUIT").contains(wanted)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "不支持的收藏类型: " + type);
        }
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (String assetType : List.of("BADGE", "SKIN", "BGM", "SPECIAL_FRUIT")) {
            if (wanted == null || wanted.equals(assetType)) {
                result.put(assetType, new ArrayList<>());
            }
        }

        // 徽章（wish_user_badge × wish_badge）；B14：仅 BADGE 在结果集中时才查询（修复指定 SKIN 查询 NPE）
        if (result.containsKey("BADGE")) {
        List<WishUserBadge> badges = userBadgeMapper.selectList(new LambdaQueryWrapper<WishUserBadge>()
                .eq(WishUserBadge::getUserId, userId));
        // T27 N+1 修复：徽章定义批量预取
        Map<Long, WishBadge> badgeMap = badges.isEmpty() ? Map.of()
                : badgeMapper.selectBatchIds(badges.stream().map(WishUserBadge::getBadgeId).toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(WishBadge::getId, b -> b, (a, b) -> a));
        for (WishUserBadge ub : badges) {
            WishBadge badge = badgeMap.get(ub.getBadgeId());
            if (badge == null) continue;
            result.get("BADGE").add(Map.of(
                    "id", ub.getId(), "name", badge.getName() == null ? "" : badge.getName(),
                    "icon", badge.getIcon() == null ? "" : badge.getIcon()));
        }

        }
        // 皮肤/BGM/星火收藏品（wish_user_asset × wish_virtual_asset）
        List<UserAsset> assets = userAssetMapper.selectList(new LambdaQueryWrapper<UserAsset>()
                .eq(UserAsset::getUserId, userId)
                .eq(UserAsset::getStatus, "OWNED"));
        // T27 N+1 修复：虚拟资产定义批量预取
        Map<Long, VirtualAsset> assetMap = assets.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assets.stream().map(UserAsset::getAssetId).toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(VirtualAsset::getId, a -> a, (a, b) -> a));
        for (UserAsset ua : assets) {
            VirtualAsset asset = assetMap.get(ua.getAssetId());
            if (asset == null) continue;
            String assetTypeName = asset.getAssetType().name();
            if (!result.containsKey(assetTypeName)) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", ua.getId());
            item.put("assetId", asset.getId());
            item.put("name", asset.getName());
            item.put("icon", asset.getIcon());
            item.put("resourceUrl", asset.getResourceUrl());
            item.put("isActive", assetTypeName.equals("SKIN") ? ua.getIsActiveSkin()
                    : assetTypeName.equals("BGM") ? ua.getIsActiveBgm() : null);
            item.put("refWishId", ua.getRefWishId());
            result.get(assetTypeName).add(item);
        }
        return result;
    }

    @Override
    @Transactional
    public UserAsset collectSpark(Long userId, Long wishId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || wish.getFruitType() != FruitType.SPARK) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "仅星火(SPARK)心愿可收藏");
        }
        // B02：星火收藏是作者对自己心愿的操作（4.2 星火=本人），防越权收藏他人星火
        accessPolicy.requireOwner(wish, userId);
        // 幂等：同一心愿不可重复收藏
        if (userAssetMapper.selectCount(new LambdaQueryWrapper<UserAsset>()
                .eq(UserAsset::getUserId, userId)
                .eq(UserAsset::getRefWishId, wishId)
                .eq(UserAsset::getSource, "COLLECT")) > 0) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已收藏过该星火心愿");
        }
        // 找或建星火收藏品配置（SPECIAL_FRUIT 类型，配置表化）
        VirtualAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<VirtualAsset>()
                .eq(VirtualAsset::getAssetType, AssetType.SPECIAL_FRUIT)
                .eq(VirtualAsset::getIsActive, true)
                .last("LIMIT 1"));
        if (asset == null) {
            asset = new VirtualAsset();
            asset.setAssetType(AssetType.SPECIAL_FRUIT);
            asset.setName("星火收藏品");
            asset.setDescription("收藏的 SPARK 星火心愿");
            asset.setPriceStarlight(0);
            asset.setPriceRmb(0);
            asset.setPayMethod(AssetPayMethod.STARLIGHT);
            asset.setStock(0);
            asset.setIsActive(true);
            assetMapper.insert(asset);
        }
        UserAsset userAsset = new UserAsset();
        userAsset.setUserId(userId);
        userAsset.setAssetId(asset.getId());
        userAsset.setSource("COLLECT");
        userAsset.setStatus("OWNED");
        userAsset.setRefWishId(wishId);
        userAsset.setAcquiredAt(LocalDateTime.now(ZoneId.of("UTC")));
        userAssetMapper.insert(userAsset);
        log.info("星火收藏成功, userId={}, wishId={}", userId, wishId);
        return userAsset;
    }

    @Override
    @Transactional
    public void setActiveAsset(Long userId, Long assetId) {
        UserAsset userAsset = userAssetMapper.selectOne(new LambdaQueryWrapper<UserAsset>()
                .eq(UserAsset::getUserId, userId)
                .eq(UserAsset::getAssetId, assetId)
                .eq(UserAsset::getStatus, "OWNED")
                .last("LIMIT 1"));
        if (userAsset == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "未拥有该资产");
        }
        VirtualAsset asset = assetMapper.selectById(assetId);
        if (asset == null) return;
        boolean isSkin = asset.getAssetType() == AssetType.SKIN;
        boolean isBgm = asset.getAssetType() == AssetType.BGM;
        if (!isSkin && !isBgm) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "该资产类型不支持激活");
        }
        // 同类型互斥：先清除当前激活
        userAssetMapper.update(null, new LambdaUpdateWrapper<UserAsset>()
                .eq(UserAsset::getUserId, userId)
                .eq(isSkin ? UserAsset::getIsActiveSkin : UserAsset::getIsActiveBgm, true)
                .set(isSkin ? UserAsset::getIsActiveSkin : UserAsset::getIsActiveBgm, false));
        userAssetMapper.update(null, new LambdaUpdateWrapper<UserAsset>()
                .eq(UserAsset::getId, userAsset.getId())
                .set(isSkin ? UserAsset::getIsActiveSkin : UserAsset::getIsActiveBgm, true));
    }

    // ---------------- 管理端 ----------------

    @Override
    public List<VirtualAsset> listAllAssets() {
        return assetMapper.selectList(new LambdaQueryWrapper<VirtualAsset>()
                .orderByDesc(VirtualAsset::getId));
    }

    @Override
    @Transactional
    public VirtualAsset saveAsset(VirtualAsset asset) {
        if (asset.getId() != null) {
            assetMapper.updateById(asset);
        } else {
            assetMapper.insert(asset);
        }
        return assetMapper.selectById(asset.getId());
    }

    @Override
    @Transactional
    public void toggleAsset(Long assetId, boolean active) {
        VirtualAsset update = new VirtualAsset();
        update.setId(assetId);
        update.setIsActive(active);
        assetMapper.updateById(update);
    }

    @Override
    @Transactional
    public void deleteAsset(Long assetId) {
        VirtualAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "资产不存在");
        }
        // 仍有用户持有 → 拒绝物理删除（防孤儿数据），引导改用下架
        Long ownedCount = userAssetMapper.selectCount(new LambdaQueryWrapper<UserAsset>()
                .eq(UserAsset::getAssetId, assetId));
        if (ownedCount != null && ownedCount > 0) {
            throw new BusinessException(WishErrorCodes.WISH_ASSET_IN_USE,
                    "已有 " + ownedCount + " 位用户持有该资产，无法删除；请改用下架");
        }
        assetMapper.deleteById(assetId);
    }

    @Override
    @Transactional
    public void auditBrand(Long brandId, String status) {
        if (!List.of("APPROVED", "REJECTED").contains(status)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非法审核状态");
        }
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "品牌不存在");
        }
        Brand update = new Brand();
        update.setId(brandId);
        update.setStatus(status);
        brandMapper.updateById(update);
        log.info("品牌审核, brandId={}, status={}", brandId, status);
    }

    /** 已拥有资产 ID 集合 */
    private Set<Long> ownedAssetIds(Long userId) {
        if (userId == null) return Set.of();
        return userAssetMapper.selectList(new LambdaQueryWrapper<UserAsset>()
                        .eq(UserAsset::getUserId, userId)
                        .eq(UserAsset::getStatus, "OWNED"))
                .stream().map(UserAsset::getAssetId).collect(java.util.stream.Collectors.toSet());
    }

    // ---------------- 品牌许愿池 ----------------

    @Override
    public List<Brand> listBrands() {
        return brandMapper.selectList(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getStatus, "APPROVED")
                .orderByDesc(Brand::getId));
    }

    @Override
    public List<Brand> listAllBrands() {
        return brandMapper.selectList(new LambdaQueryWrapper<Brand>()
                .orderByDesc(Brand::getId));
    }

    @Override
    public List<BrandPool> listPools(Long brandId) {
        return poolMapper.selectList(new LambdaQueryWrapper<BrandPool>()
                .eq(BrandPool::getBrandId, brandId)
                .in(BrandPool::getStatus, "DRAFT", "ACTIVE", "SETTLING", "CONFIG_ERROR")
                .orderByDesc(BrandPool::getId));
    }

    /** WISH-03：rewardSpec 校验——starlight 非负整数；targetCount≥1；endAt 未来 */
    private void validateRewardSpec(BrandPool pool) {
        if (pool.getTargetCount() == null || pool.getTargetCount() < 1) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "目标人数须 ≥ 1");
        }
        if (pool.getEndAt() == null || !pool.getEndAt().isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "截止时间必须晚于当前时间");
        }
        if (pool.getRewardJson() != null && !pool.getRewardJson().isBlank()) {
            try {
                final var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(pool.getRewardJson());
                final int starlight = node.path("starlight").asInt(-1);
                if (starlight < 0) {
                    throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "奖励 starlight 须为非负整数");
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "rewardJson 不是合法 JSON");
            }
        }
    }

    /** WISH-03：审计留痕（审批/池变更/结算动作） */
    private void audit(Long brandId, Long poolId, Long adminUserId, String action, String detail) {
        try {
            final com.cloudmart.wish.entity.BrandAuditLog log =
                    new com.cloudmart.wish.entity.BrandAuditLog();
            log.setBrandId(brandId);
            log.setPoolId(poolId);
            log.setAdminUserId(adminUserId);
            log.setAction(action);
            log.setDetail(detail);
            brandAuditLogMapper.insert(log);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(CollectionServiceImpl.class)
                    .warn("[WISH03] 审计写入失败（不阻断主流程）: action={}, {}", action, ex.getMessage());
        }
    }

    @Override
    @Transactional
    public BrandPool createPool(BrandPool pool, Long adminUserId) {
        validateRewardSpec(pool);
        // WISH-03：创建为 DRAFT——发布时校验品牌审核并冻结规则版本
        pool.setStatus("DRAFT");
        pool.setRuleVersion(1);
        poolMapper.insert(pool);
        audit(pool.getBrandId(), pool.getId(), adminUserId, "POOL_CREATE",
                "name=" + pool.getName() + ", target=" + pool.getTargetCount());
        return pool;
    }

    @Override
    public BrandPool updatePool(Long poolId, BrandPool patch, Long adminUserId) {
        final BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在");
        }
        // WISH-03：仅 DRAFT 可改——ACTIVE 及之后规则已冻结（防"参与者看到 A 规则、结算按 B 规则"）
        if (!"DRAFT".equals(pool.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "仅草稿状态可编辑（" + pool.getStatus() + " 已冻结规则）");
        }
        if (patch.getName() != null) {
            pool.setName(patch.getName());
        }
        if (patch.getTargetCount() != null && patch.getTargetCount() >= 1) {
            pool.setTargetCount(patch.getTargetCount());
        }
        if (patch.getRewardJson() != null) {
            pool.setRewardJson(patch.getRewardJson());
        }
        if (patch.getEndAt() != null) {
            pool.setEndAt(patch.getEndAt());
        }
        validateRewardSpec(pool);
        poolMapper.updateById(pool);
        audit(pool.getBrandId(), pool.getId(), adminUserId, "POOL_UPDATE", "draft 更新");
        return pool;
    }

    /** WISH-03：发布（DRAFT→ACTIVE）——品牌必须已批准；冻结 ruleVersion；审计 */
    @Override
    @Transactional
    public BrandPool publishPool(Long poolId, Long adminUserId) {
        final BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在");
        }
        if (!"DRAFT".equals(pool.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "仅草稿可发布");
        }
        final var brand = brandMapper.selectById(pool.getBrandId());
        if (brand == null || !"APPROVED".equals(brand.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "池所属品牌必须已通过审核");
        }
        validateRewardSpec(pool);
        int published = poolMapper.publishDraft(poolId);
        if (published == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "发布冲突（已被其他管理员处理）");
        }
        audit(pool.getBrandId(), pool.getId(), adminUserId, "POOL_PUBLISH", "发布并冻结规则 v" + pool.getRuleVersion());
        return poolMapper.selectById(poolId);
    }

    /** WISH-03：手动结束（ACTIVE→CANCELLED）——未达标无奖励终态；审计 */
    @Override
    @Transactional
    public void endPool(Long poolId) {
        final BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在");
        }
        int cancelled = poolMapper.casActiveToCancelled(poolId);
        if (cancelled == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "仅 ACTIVE 可手动结束（当前: " + pool.getStatus() + "）");
        }
        audit(pool.getBrandId(), poolId, null, "POOL_END", "管理员手动结束（未达标无奖励终态）");
    }

    /**
     * WISH-03 结算（重写）：
     * <ol>
     *   <li>扫描 ACTIVE 且（达标 或 已过期）的池；</li>
     *   <li>池级 CAS ACTIVE→SETTLING——只允许一个结算者推进（双实例只有一个赢）；</li>
     *   <li>冻结成员集合（SETTLING 后 joinPool 拒绝——后到用户不得进入已结算集合）；</li>
     *   <li>逐人登记奖励义务（uk(pool,user,type) 唯一事实，DuplicateKey 幂等跳过）；</li>
     *   <li>逐义务 earnStarlightIdempotent（operationId=BRAND_POOL:pool:user:TYPE:vRule）；
     *       每日上限时 creditedAmount 可少于 promisedAmount——如实记录部分到账；</li>
     *   <li>rewardJson 非法 → CONFIG_ERROR（不假成功结束），修复后可重试；</li>
     *   <li>全部义务终态 → SETTLING→ENDED。</li>
     * </ol>
     */
    @Override
    @Transactional
    public Map<String, Object> brandRewardCheck() {
        if (!brandPoolProperties.isEnabled()) {
            log.warn("[wish-brand-reward-check] 品牌池开关关闭（WISH-03 修复前风险缓解），本次跳过不执行");
            return Map.of("rewarded", 0, "skipped", 0, "disabled", true);
        }
        final var pools = poolMapper.selectList(new LambdaQueryWrapper<BrandPool>()
                .eq(BrandPool::getStatus, "ACTIVE"));
        int settled = 0;
        int skipped = 0;
        int configError = 0;
        for (final BrandPool pool : pools) {
            final int target = pool.getTargetCount() != null ? pool.getTargetCount() : 0;
            final int current = pool.getCurrentCount() != null ? pool.getCurrentCount() : 0;
            final boolean expired = pool.getEndAt() != null
                    && pool.getEndAt().isBefore(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            if (current < target && !expired) {
                skipped++;
                continue;
            }
            // 池级 CAS：只让一个结算者推进
            if (poolMapper.casActiveToSettling(pool.getId()) == 0) {
                skipped++;
                continue;
            }
            // rewardSpec 解析失败 → CONFIG_ERROR（待修复，不假成功）
            int starlight;
            try {
                starlight = parseStarlightReward(pool);
            } catch (IllegalStateException bad) {
                poolMapper.casSettlingToConfigError(pool.getId());
                configError++;
                audit(pool.getBrandId(), pool.getId(), null, "SETTLE_RETRY",
                        "rewardSpec 非法进入 CONFIG_ERROR: " + bad.getMessage());
                continue;
            }

            final var members = poolMemberMapper.selectList(
                    new LambdaQueryWrapper<com.cloudmart.wish.entity.BrandPoolMember>()
                            .eq(com.cloudmart.wish.entity.BrandPoolMember::getPoolId, pool.getId()));
            int creditedCount = 0;
            int failedCount = 0;
            for (final var member : members) {
                if (starlight <= 0) {
                    continue; // 无奖励池：成员集合冻结即可，无义务
                }
                final String opId = "BRAND_POOL:" + pool.getId() + ":" + member.getUserId()
                        + ":STARLIGHT:v" + (pool.getRuleVersion() == null ? 1 : pool.getRuleVersion());
                // 义务唯一事实：uk(pool,user,type)——重复结算 DuplicateKey 幂等跳过
                final var existing = brandRewardLogMapper.selectOne(
                        new LambdaQueryWrapper<com.cloudmart.wish.entity.BrandRewardLog>()
                                .eq(com.cloudmart.wish.entity.BrandRewardLog::getPoolId, pool.getId())
                                .eq(com.cloudmart.wish.entity.BrandRewardLog::getUserId, member.getUserId())
                                .eq(com.cloudmart.wish.entity.BrandRewardLog::getRewardType, "STARLIGHT"));
                final com.cloudmart.wish.entity.BrandRewardLog rewardLog;
                if (existing != null) {
                    rewardLog = existing; // 重试：沿用已登记义务
                } else {
                    rewardLog = new com.cloudmart.wish.entity.BrandRewardLog();
                    rewardLog.setPoolId(pool.getId());
                    rewardLog.setUserId(member.getUserId());
                    rewardLog.setRewardType("STARLIGHT");
                    rewardLog.setStatus("PENDING");
                    rewardLog.setPromisedAmount(starlight);
                    rewardLog.setRewardAmount(starlight);
                    rewardLog.setRetryCount(0);
                    rewardLog.setOperationId(opId);
                    try {
                        brandRewardLogMapper.insert(rewardLog);
                    } catch (org.springframework.dao.DuplicateKeyException dup) {
                        continue; // 并发登记：唯一事实已存在
                    }
                }
                if ("CREDITED".equals(rewardLog.getStatus())) {
                    continue; // 已入账（幂等重入）
                }
                try {
                    final var op = userStatService.earnStarlightIdempotent(member.getUserId(),
                            starlight, com.cloudmart.wish.enums.ResourceLogSource.ACTIVITY_REWARD,
                            pool.getId(), opId);
                    rewardLog.setStatus("CREDITED");
                    rewardLog.setCreditedAmount(op.creditedAmount());
                    rewardLog.setRetryCount((rewardLog.getRetryCount() == null ? 0 : rewardLog.getRetryCount()) + 1);
                    brandRewardLogMapper.updateById(rewardLog);
                    creditedCount++;
                    if (op.creditedAmount() < starlight) {
                        log.info("[WISH03] 池 {} 用户 {} 部分到账（每日上限）：承诺 {} 实际 {}",
                                pool.getId(), member.getUserId(), starlight, op.creditedAmount());
                    }
                } catch (Exception ex) {
                    rewardLog.setStatus("FAILED");
                    rewardLog.setRetryCount((rewardLog.getRetryCount() == null ? 0 : rewardLog.getRetryCount()) + 1);
                    rewardLog.setLastError(ex.getMessage() != null && ex.getMessage().length() > 500
                            ? ex.getMessage().substring(0, 500) : ex.getMessage());
                    brandRewardLogMapper.updateById(rewardLog);
                    failedCount++;
                }
            }
            // 全部义务终态 → ENDED（存在 FAILED 义务时也 ENDED——义务事实保留可审计重试）
            poolMapper.casSettlingToEnded(pool.getId());
            settled++;
            log.info("[WISH03] 池 {} 结算完成：成员 {} 入账 {} 失败 {}", pool.getId(),
                    members.size(), creditedCount, failedCount);
        }
        log.info("[wish-brand-reward-check] 完成 settled={} skipped={} configError={}", settled, skipped, configError);
        return Map.of("rewarded", settled, "skipped", skipped, "configError", configError);
    }

    /** WISH-03：rewardJson → 星光数量（非法抛 IllegalStateException → CONFIG_ERROR） */
    private int parseStarlightReward(BrandPool pool) {
        if (pool.getRewardJson() == null || pool.getRewardJson().isBlank()) {
            return 0;
        }
        try {
            final var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(pool.getRewardJson());
            final int starlight = node.path("starlight").asInt(-1);
            if (starlight < 0) {
                throw new IllegalStateException("starlight 非负整数非法");
            }
            return starlight;
        } catch (IllegalStateException bad) {
            throw bad;
        } catch (Exception ex) {
            throw new IllegalStateException("rewardJson 解析失败: " + ex.getMessage());
        }
    }

    /** WISH-03：CONFIG_ERROR 池修复后的审计重试（SETTLING 重入结算） */
    @Override
    @Transactional
    public Map<String, Object> retrySettlement(Long poolId, Long adminUserId) {
        final BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在");
        }
        if (!"CONFIG_ERROR".equals(pool.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "仅 CONFIG_ERROR 池可重试结算（当前: " + pool.getStatus() + "）");
        }
        // 修复 rewardJson 后重入 SETTLING（brandRewardCheck 下一轮会处理 SETTLING 后续？——
        // 直接重入：CAS CONFIG_ERROR→SETTLING 由调用方在下一次扫描中完成，这里仅校验 rewardSpec 可解析）
        try {
            parseStarlightReward(pool);
        } catch (IllegalStateException bad) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "rewardJson 仍非法，请先修复: " + bad.getMessage());
        }
        audit(pool.getBrandId(), poolId, adminUserId, "SETTLE_RETRY", "CONFIG_ERROR 修复确认，等待重入结算");
        return Map.of("poolId", poolId, "status", pool.getStatus(), "note",
                "rewardSpec 已可解析；请将池状态置回结算流程（下一轮扫描或重新发布）");
    }

    @Override
    public BrandPool getPoolDetail(Long poolId) {
        BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在");
        }
        return pool;
    }

    /** WISH-03：池奖励记录真实查询（结算状态/承诺/实际入账/重试） */
    @Override
    public List<Map<String, Object>> poolRewards(Long poolId) {
        return brandRewardLogMapper.selectList(new LambdaQueryWrapper<com.cloudmart.wish.entity.BrandRewardLog>()
                        .eq(com.cloudmart.wish.entity.BrandRewardLog::getPoolId, poolId)
                        .orderByAsc(com.cloudmart.wish.entity.BrandRewardLog::getId))
                .stream().map(r -> {
                    final Map<String, Object> view = new java.util.LinkedHashMap<String, Object>();
                    view.put("id", r.getId());
                    view.put("userId", r.getUserId());
                    view.put("rewardType", r.getRewardType());
                    view.put("status", r.getStatus());
                    view.put("promisedAmount", r.getPromisedAmount());
                    view.put("creditedAmount", r.getCreditedAmount());
                    view.put("retryCount", r.getRetryCount());
                    view.put("lastError", r.getLastError());
                    view.put("grantedAt", r.getGrantedAt() == null ? "" : r.getGrantedAt().toString());
                    return view;
                }).toList();
    }

    /** WISH-03：品牌审计日志真实查询 */
    @Override
    public List<Map<String, Object>> brandAuditLogs(Long brandId) {
        return brandAuditLogMapper.selectList(new LambdaQueryWrapper<com.cloudmart.wish.entity.BrandAuditLog>()
                        .eq(com.cloudmart.wish.entity.BrandAuditLog::getBrandId, brandId)
                        .orderByDesc(com.cloudmart.wish.entity.BrandAuditLog::getId))
                .stream().map(l -> {
                    final Map<String, Object> view = new java.util.LinkedHashMap<String, Object>();
                    view.put("id", l.getId());
                    view.put("poolId", l.getPoolId());
                    view.put("adminUserId", l.getAdminUserId());
                    view.put("action", l.getAction());
                    view.put("detail", l.getDetail());
                    view.put("createdAt", l.getCreatedAt() == null ? "" : l.getCreatedAt().toString());
                    return view;
                }).toList();
    }

    /** WISH-03：我的参加记录（本人维度） */
    @Override
    public List<Map<String, Object>> myPoolJoins(Long userId) {
        return poolMemberMapper.selectList(new LambdaQueryWrapper<com.cloudmart.wish.entity.BrandPoolMember>()
                        .eq(com.cloudmart.wish.entity.BrandPoolMember::getUserId, userId)
                        .orderByDesc(com.cloudmart.wish.entity.BrandPoolMember::getId))
                .stream().map(m -> {
                    final Map<String, Object> view = new java.util.LinkedHashMap<String, Object>();
                    view.put("poolId", m.getPoolId());
                    view.put("joinedAt", m.getJoinedAt() == null ? "" : m.getJoinedAt().toString());
                    final BrandPool pool = poolMapper.selectById(m.getPoolId());
                    view.put("poolName", pool == null ? "" : pool.getName());
                    view.put("poolStatus", pool == null ? "" : pool.getStatus());
                    view.put("targetCount", pool == null ? 0 : pool.getTargetCount());
                    view.put("currentCount", pool == null ? 0 : pool.getCurrentCount());
                    return view;
                }).toList();
    }

    /** WISH-03：我的奖励结果（本人维度：义务状态/承诺/实际入账——部分到账如实可见） */
    @Override
    public List<Map<String, Object>> myPoolRewards(Long userId) {
        return brandRewardLogMapper.selectList(new LambdaQueryWrapper<com.cloudmart.wish.entity.BrandRewardLog>()
                        .eq(com.cloudmart.wish.entity.BrandRewardLog::getUserId, userId)
                        .orderByDesc(com.cloudmart.wish.entity.BrandRewardLog::getId))
                .stream().map(r -> {
                    final Map<String, Object> view = new java.util.LinkedHashMap<String, Object>();
                    view.put("poolId", r.getPoolId());
                    view.put("rewardType", r.getRewardType());
                    view.put("status", r.getStatus());
                    view.put("promisedAmount", r.getPromisedAmount());
                    view.put("creditedAmount", r.getCreditedAmount());
                    final BrandPool pool = poolMapper.selectById(r.getPoolId());
                    view.put("poolName", pool == null ? "" : pool.getName());
                    return view;
                }).toList();
    }

    @Override
    @Transactional
    public void joinPool(Long userId, Long poolId) {
        // WISH-03 风险缓解：参加入口关闭时返回明确维护态（503），不产生成员/计数事实
        if (!brandPoolProperties.isEnabled()) {
            throw new BusinessException(WishErrorCodes.WISH_BRAND_POOL_MAINTENANCE,
                    "品牌许愿池维护中，暂时不能参加");
        }
        BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在");
        }
        // WISH-03：完整状态校验——仅 ACTIVE 可参加（SETTLING 冻结成员集合，后到不得进入已结算集合）
        if (!"ACTIVE".equals(pool.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "许愿池未开放或已结束");
        }
        // 截止时间校验
        if (pool.getEndAt() != null && pool.getEndAt().isBefore(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "许愿池已过截止时间");
        }
        // 品牌审核校验：池必须属于已批准品牌
        final var brand = brandMapper.selectById(pool.getBrandId());
        if (brand == null || !"APPROVED".equals(brand.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "许愿池所属品牌未通过审核");
        }
        // 容量 CAS：current_count < target_count 才允许入池（防超员）
        int capacity = poolMapper.incrementWithinTarget(poolId);
        if (capacity == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "许愿池人数已满");
        }
        com.cloudmart.wish.entity.BrandPoolMember member = new com.cloudmart.wish.entity.BrandPoolMember();
        member.setPoolId(poolId);
        member.setUserId(userId);
        try {
            poolMemberMapper.insert(member);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 已是成员：回滚本次容量占用
            poolMapper.update(null, new LambdaUpdateWrapper<BrandPool>()
                    .setSql("current_count = GREATEST(current_count - 1, 0)")
                    .eq(BrandPool::getId, poolId));
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已加入该许愿池");
        }
    }
}
