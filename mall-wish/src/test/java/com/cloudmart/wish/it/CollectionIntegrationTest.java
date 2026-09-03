package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.UserAsset;
import com.cloudmart.wish.service.CollectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 虚拟收藏 + 品牌合作集成测试（Sprint 3.6，真实 MySQL+Redis）。
 *
 * <p>覆盖文档 3.6 验收：星光兑换闭环/兑换幂等/余额不足 402/限量 Redis
 * 预扣/皮肤切换互斥/星火收藏品/品牌许愿池/下架。</p>
 */
@DisplayName("虚拟收藏 + 品牌合作集成测试")
class CollectionIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private CollectionService collectionService;

    private static final long USER = 950L;
    private static final double FENCE = 0;

    private Long seedAsset(String type, int priceStarlight, int stock) {
        com.cloudmart.wish.entity.VirtualAsset asset = new com.cloudmart.wish.entity.VirtualAsset();
        asset.setAssetType(com.cloudmart.wish.enums.AssetType.valueOf(type));
        asset.setName("测试资产-" + type);
        asset.setPriceStarlight(priceStarlight);
        asset.setPriceRmb(0);
        asset.setPayMethod(com.cloudmart.wish.enums.AssetPayMethod.STARLIGHT);
        asset.setStock(stock);
        asset.setIsActive(true);
        return collectionService.saveAsset(asset).getId();
    }

    @Nested
    @DisplayName("星光兑换")
    class Exchange {

        @Test
        @DisplayName("兑换成功：扣星光 + 用户资产 OWNED；重复兑换 → 已拥有（幂等验收）")
        void exchangeSuccessAndIdempotent() {
            seedUserStat(USER, 100);
            Long assetId = seedAsset("SKIN", 10, 0);

            UserAsset ua = collectionService.exchange(USER, assetId, "STARLIGHT");
            assertThat(ua.getStatus()).isEqualTo("OWNED");

            Integer balance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, USER);
            assertThat(balance).isEqualTo(90);

            // 幂等：重复兑换 → 已拥有（409）
            assertThatThrownBy(() -> collectionService.exchange(USER, assetId, "STARLIGHT"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("余额不足 → 402 WISH_STARLIGHT_INSUFFICIENT")
        void insufficientStarlight() {
            seedUserStat(USER, 5);
            Long assetId = seedAsset("SKIN", 10, 0);
            assertThatThrownBy(() -> collectionService.exchange(USER, assetId, "STARLIGHT"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT);
        }

        @Test
        @DisplayName("限量资产：Redis DECR 预扣 → 库存耗尽拒绝")
        void stockLimit() {
            seedUserStat(USER, 1000);
            Long assetId = seedAsset("BGM", 1, 1);
            collectionService.exchange(USER, assetId, "STARLIGHT");
            // 库存耗尽（stock=1，第二次 → 售罄）
            assertThatThrownBy(() -> collectionService.exchange(901L, assetId, "STARLIGHT"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("皮肤/BGM 切换")
    class SkinSwitch {

        @Test
        @DisplayName("皮肤切换：同类型互斥，即时生效（验收）")
        void skinSwitchMutualExclusion() {
            seedUserStat(USER, 100);
            Long skinA = seedAsset("SKIN", 0, 0);
            Long skinB = seedAsset("SKIN", 0, 0);

            // 0 星光资产仍走星光路径（price=0 → 直接获得）
            UserAsset ua1 = collectionService.exchange(USER, skinA, "STARLIGHT");
            UserAsset ua2 = collectionService.exchange(USER, skinB, "STARLIGHT");

            collectionService.setActiveAsset(USER, skinA);
            Boolean activeA = jdbcTemplate.queryForObject(
                    "SELECT is_active_skin FROM wish_user_asset WHERE user_id = ? AND asset_id = ?",
                    Boolean.class, USER, skinA);
            assertThat(activeA).isTrue();

            // 切换到 B → A 取消激活
            collectionService.setActiveAsset(USER, skinB);
            Boolean activeAAfter = jdbcTemplate.queryForObject(
                    "SELECT is_active_skin FROM wish_user_asset WHERE user_id = ? AND asset_id = ?",
                    Boolean.class, USER, skinA);
            Boolean activeB = jdbcTemplate.queryForObject(
                    "SELECT is_active_skin FROM wish_user_asset WHERE user_id = ? AND asset_id = ?",
                    Boolean.class, USER, skinB);
            assertThat(activeAAfter).isFalse();
            assertThat(activeB).isTrue();
        }
    }

    @Nested
    @DisplayName("星火收藏品")
    class SparkCollect {

        @Test
        @DisplayName("收藏 SPARK 心愿 → SPECIAL_FRUIT 资产（幂等：重复收藏拒绝）")
        void sparkCollect() {
            long wishId = System.nanoTime();
            jdbcTemplate.update("""
                    INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                      audit_status, is_visible, fruit_type, created_at, updated_at)
                    VALUES (?, 950, '星火心愿', '测试', 1, 'PUBLIC', 'ACTIVE', 'APPROVED', 1, 'SPARK', NOW(), NOW())
                    """, wishId);

            collectionService.collectSpark(USER, wishId);
            assertThatThrownBy(() -> collectionService.collectSpark(USER, wishId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);

            // 收藏馆含 SPECIAL_FRUIT 条目
            var collections = collectionService.collections(USER);
            assertThat(collections.get("SPECIAL_FRUIT")).hasSize(1);
            assertThat(collections.get("SPECIAL_FRUIT").get(0).get("refWishId")).isEqualTo(wishId);
        }

        @Test
        @DisplayName("非 SPARK 心愿 → 拒绝收藏")
        void nonSparkRejected() {
            long wishId = System.nanoTime();
            jdbcTemplate.update("""
                    INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                      audit_status, is_visible, fruit_type, created_at, updated_at)
                    VALUES (?, 950, '微光心愿', '测试', 1, 'PUBLIC', 'ACTIVE', 'APPROVED', 1, 'GLOW', NOW(), NOW())
                    """, wishId);
            assertThatThrownBy(() -> collectionService.collectSpark(USER, wishId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("管理端资产维护")
    class AdminAssetMaintenance {

        @Test
        @DisplayName("带 id 保存 → 更新原行（不产生新记录）；无 id → 新建")
        void saveAssetUpdateVsInsert() {
            Long assetId = seedAsset("SKIN", 10, 0);
            Integer before = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_virtual_asset", Integer.class);

            // 编辑：带 id 保存 → updateById
            com.cloudmart.wish.entity.VirtualAsset edit = new com.cloudmart.wish.entity.VirtualAsset();
            edit.setId(assetId);
            edit.setName("改名后的资产");
            edit.setPriceStarlight(88);
            collectionService.saveAsset(edit);

            Integer afterUpdate = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_virtual_asset", Integer.class);
            assertThat(afterUpdate).isEqualTo(before);

            String dbName = jdbcTemplate.queryForObject(
                    "SELECT name FROM wish_virtual_asset WHERE id = ?", String.class, assetId);
            Integer dbPrice = jdbcTemplate.queryForObject(
                    "SELECT price_starlight FROM wish_virtual_asset WHERE id = ?", Integer.class, assetId);
            assertThat(dbName).isEqualTo("改名后的资产");
            assertThat(dbPrice).isEqualTo(88);

            // 新建：无 id → insert
            com.cloudmart.wish.entity.VirtualAsset create = new com.cloudmart.wish.entity.VirtualAsset();
            create.setAssetType(com.cloudmart.wish.enums.AssetType.BGM);
            create.setName("全新资产");
            create.setPriceStarlight(5);
            create.setPayMethod(com.cloudmart.wish.enums.AssetPayMethod.STARLIGHT);
            create.setStock(0);
            create.setIsActive(true);
            Long newId = collectionService.saveAsset(create).getId();
            assertThat(newId).isNotNull().isNotEqualTo(assetId);
            Integer afterInsert = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_virtual_asset", Integer.class);
            assertThat(afterInsert).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("下架 → 兑换拒绝；重新上架 → 可兑换")
        void toggleAssetActive() {
            seedUserStat(USER, 100);
            Long assetId = seedAsset("SKIN", 10, 0);

            collectionService.toggleAsset(assetId, false);
            Boolean dbActive = jdbcTemplate.queryForObject(
                    "SELECT is_active FROM wish_virtual_asset WHERE id = ?", Boolean.class, assetId);
            assertThat(dbActive).isFalse();
            assertThatThrownBy(() -> collectionService.exchange(USER, assetId, "STARLIGHT"))
                    .isInstanceOf(BusinessException.class);

            collectionService.toggleAsset(assetId, true);
            UserAsset ua = collectionService.exchange(USER, assetId, "STARLIGHT");
            assertThat(ua.getStatus()).isEqualTo("OWNED");
        }
    }

    @Nested
    @DisplayName("品牌许愿池")
    class BrandPool {
        @Test
        @DisplayName("加入许愿池 → 计数+1；重复加入拒绝（幂等验收）")
        void joinPoolFlow() {
            // 品牌入驻 → APPROVED
            com.cloudmart.wish.entity.Brand brand = new com.cloudmart.wish.entity.Brand();
            brand.setName("测试品牌");
            brand.setStatus("APPROVED");
            brand.setCreatedBy(1L);
            // 直接 insert brand
            jdbcTemplate.update("""
                    INSERT INTO wish_brand (id, name, status, created_by, created_at, updated_at)
                    VALUES (?, '测试品牌', 'APPROVED', 1, NOW(), NOW())
                    """, 1L);

            // 创建许愿池
            com.cloudmart.wish.entity.BrandPool pool = new com.cloudmart.wish.entity.BrandPool();
            pool.setBrandId(1L);
            pool.setCategoryId(1L);
            pool.setName("极光许愿池");
            pool.setTargetCount(100);
            var created = collectionService.createPool(pool, 1L);

            collectionService.joinPool(USER, created.getId());
            assertThatThrownBy(() -> collectionService.joinPool(USER, created.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT current_count FROM wish_brand_pool WHERE id = ?",
                    Integer.class, created.getId());
            assertThat(count).isEqualTo(1);
        }
    }
}
