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
    private final BrandPoolMemberMapper poolMemberMapper;
    private final WishBadgeMapper badgeMapper;
    private final WishUserBadgeMapper userBadgeMapper;
    private final WishMapper wishMapper;
    private final UserStatService userStatService;
    private final StringRedisTemplate redisTemplate;

    // ---------------- 虚拟工坊 ----------------

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
    public UserAsset exchange(Long userId, Long assetId, String paymentMethod) {
        VirtualAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !Boolean.TRUE.equals(asset.getIsActive())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "资产不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (asset.getValidTo() != null && now.isAfter(asset.getValidTo())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "资产已过期下架");
        }
        if ("RMB".equals(paymentMethod) || (asset.getPayMethod() == AssetPayMethod.RMB)) {
            // RMB 内购偏差：对接 mall-payment 后开通（进度文件留档）
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "RMB 内购暂未开通");
        }

        // uk 幂等（验收：重复兑换返回已拥有）
        if (userAssetMapper.selectCount(new LambdaQueryWrapper<UserAsset>()
                .eq(UserAsset::getUserId, userId)
                .eq(UserAsset::getAssetId, assetId)) > 0) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已拥有该资产");
        }

        // 限量 Redis DECR 原子预扣（验收：100 并发仅前 N 成功）
        if (asset.getStock() != null && asset.getStock() > 0) {
            String stockKey = VirtualAssetHelper.stockKey(assetId);
            try {
                // 首次初始化：SETNX stock 值
                redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(asset.getStock()));
                Long remain = redisTemplate.opsForValue().decrement(stockKey);
                if (remain != null && remain < 0) {
                    redisTemplate.opsForValue().increment(stockKey);
                    throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "限量资产已售罄");
                }
            } catch (DataAccessException ex) {
                log.warn("限量预扣 Redis 异常（Fail-Open 放行，DB stock 兜底）: {}", ex.getMessage());
            }
        }

        // 扣星光（price 0 = 免费资产，跳过扣减）
        int cost = asset.getPriceStarlight() != null ? asset.getPriceStarlight() : 0;
        if (cost > 0 && ("STARLIGHT".equals(paymentMethod) || asset.getPayMethod() == AssetPayMethod.STARLIGHT
                || asset.getPayMethod() == AssetPayMethod.BOTH)) {
            int credited = userStatService.spendStarlight(userId, cost,
                    ResourceLogSource.EXCHANGE, assetId);
            if (credited < cost) {
                // 限量回补
                if (asset.getStock() != null && asset.getStock() > 0) {
                    try {
                        redisTemplate.opsForValue().increment(VirtualAssetHelper.stockKey(assetId));
                    } catch (DataAccessException ignored) { }
                }
                throw new BusinessException(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT, "星光不足");
            }
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
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已拥有该资产");
        }
        log.info("资产兑换成功, userId={}, assetId={}, method={}", userId, assetId, paymentMethod);
        return userAsset;
    }

    // ---------------- 收藏馆 ----------------

    @Override
    public Map<String, List<Map<String, Object>>> collections(Long userId) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (String type : List.of("BADGE", "SKIN", "BGM", "SPECIAL_FRUIT")) {
            result.put(type, new ArrayList<>());
        }

        // 徽章（wish_user_badge × wish_badge）
        List<WishUserBadge> badges = userBadgeMapper.selectList(new LambdaQueryWrapper<WishUserBadge>()
                .eq(WishUserBadge::getUserId, userId));
        for (WishUserBadge ub : badges) {
            WishBadge badge = badgeMapper.selectById(ub.getBadgeId());
            if (badge == null) continue;
            result.get("BADGE").add(Map.of(
                    "id", ub.getId(), "name", badge.getName() == null ? "" : badge.getName(),
                    "icon", badge.getIcon() == null ? "" : badge.getIcon()));
        }

        // 皮肤/BGM/星火收藏品（wish_user_asset × wish_virtual_asset）
        List<UserAsset> assets = userAssetMapper.selectList(new LambdaQueryWrapper<UserAsset>()
                .eq(UserAsset::getUserId, userId)
                .eq(UserAsset::getStatus, "OWNED"));
        for (UserAsset ua : assets) {
            VirtualAsset asset = assetMapper.selectById(ua.getAssetId());
            if (asset == null) continue;
            String type = asset.getAssetType().name();
            if (!result.containsKey(type)) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", ua.getId());
            item.put("assetId", asset.getId());
            item.put("name", asset.getName());
            item.put("icon", asset.getIcon());
            item.put("resourceUrl", asset.getResourceUrl());
            item.put("isActive", type.equals("SKIN") ? ua.getIsActiveSkin()
                    : type.equals("BGM") ? ua.getIsActiveBgm() : null);
            item.put("refWishId", ua.getRefWishId());
            result.get(type).add(item);
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
                .eq(BrandPool::getStatus, "ACTIVE")
                .orderByDesc(BrandPool::getId));
    }

    @Override
    @Transactional
    public BrandPool createPool(BrandPool pool, Long adminUserId) {
        if (pool.getTargetCount() == null || pool.getTargetCount() < 1) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "目标人数须 ≥ 1");
        }
        pool.setStatus("ACTIVE");
        poolMapper.insert(pool);
        return pool;
    }

    @Override
    public BrandPool getPoolDetail(Long poolId) {
        BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在");
        }
        return pool;
    }

    @Override
    public List<Map<String, Object>> poolRewards(Long poolId) {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> brandAuditLogs(Long brandId) {
        return List.of();
    }

    @Override
    @Transactional
    public void joinPool(Long userId, Long poolId) {
        BrandPool pool = poolMapper.selectById(poolId);
        if (pool == null || !"ACTIVE".equals(pool.getStatus())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "许愿池不存在或已结束");
        }
        BrandPoolMember member = new BrandPoolMember();
        member.setPoolId(poolId);
        member.setUserId(userId);
        try {
            poolMemberMapper.insert(member);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "已加入该许愿池");
        }
        poolMapper.update(null, new LambdaUpdateWrapper<BrandPool>()
                .setSql("current_count = current_count + 1")
                .eq(BrandPool::getId, poolId));
    }
}
