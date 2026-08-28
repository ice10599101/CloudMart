package com.cloudmart.wish.it;

import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.TreeFruitsQuery;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.WorldTreeService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.service.impl.WorldTreeServiceImpl;
import com.cloudmart.wish.vo.TreeFruitVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WorldTreeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 世界生命树 3D 版集成测试（真实 MySQL：V9 坐标列/聚合口径/分页 bounds）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>坐标固化链路：PUBLIC 创建/PRIVATE 转 PUBLIC 落库，PRIVATE/TREE_HOLE 不上树</li>
 *   <li>聚合计数：上树口径（visibility/audit/status/is_visible/软删/坐标非空）+
 *       Redis 缓存回填与命中（TTL 5min±30s）</li>
 *   <li>果实分页：cursor 语义、口径排除、bounds 视口过滤（含跨 0/2π 环绕窗口）、
 *       异常 bounds 兜底全量</li>
 * </ul>
 */
@DisplayName("世界生命树 3D 版集成测试")
class TreeIntegrationTest extends WishIntegrationTestBase {

    private static final long USER_ID = 1001L;

    @Autowired
    private WorldTreeService worldTreeService;

    @Autowired
    private WishService wishService;

    // ========== 坐标固化（service 链路 → 真实 DB 落库） ==========

    @Nested
    @DisplayName("坐标固化")
    class TreePositionTests {

        @Test
        @DisplayName("PUBLIC 心愿创建：theta/phi 落库非空且在球面值域")
        void createPublicWishPersistsTreePosition() {
            Long categoryId = seedCategory("TREE_POS_PUBLIC");
            stubUserFeign();

            WishCreateResultVO result = wishService.createWish(USER_ID, new CreateWishRequest(
                    "世界树上的心愿", "固化球面坐标", null, categoryId,
                    null, WishVisibility.PUBLIC, null, false, false, null, null));

            BigDecimal theta = jdbcTemplate.queryForObject(
                    "SELECT tree_theta FROM wish WHERE id = ?", BigDecimal.class, result.id());
            BigDecimal phi = jdbcTemplate.queryForObject(
                    "SELECT tree_phi FROM wish WHERE id = ?", BigDecimal.class, result.id());

            assertThat(theta).isNotNull();
            assertThat(phi).isNotNull();
            assertThat(theta.doubleValue()).isBetween(0.0, 2 * Math.PI);
            assertThat(phi.doubleValue()).isBetween(0.0, Math.PI);
        }

        @Test
        @DisplayName("PRIVATE/TREE_HOLE 心愿创建：不上树坐标为 NULL")
        void createNonPublicWishesSkipTreePosition() {
            Long categoryId = seedCategory("TREE_POS_PRIVATE");
            stubUserFeign();

            WishCreateResultVO privateWish = wishService.createWish(USER_ID, new CreateWishRequest(
                    "私密心愿", "不上树", null, categoryId,
                    null, WishVisibility.PRIVATE, null, false, false, null, null));
            WishCreateResultVO treeHoleWish = wishService.createWish(USER_ID, new CreateWishRequest(
                    "树洞心愿", "不上树", null, categoryId,
                    null, WishVisibility.TREE_HOLE, null, false, false, null, null));

            Integer privateCoords = jdbcTemplate.queryForObject(
                    "SELECT (tree_theta IS NULL) + (tree_phi IS NULL) FROM wish WHERE id = ?",
                    Integer.class, privateWish.id());
            Integer treeHoleCoords = jdbcTemplate.queryForObject(
                    "SELECT (tree_theta IS NULL) + (tree_phi IS NULL) FROM wish WHERE id = ?",
                    Integer.class, treeHoleWish.id());

            assertThat(privateCoords).isEqualTo(2);
            assertThat(treeHoleCoords).isEqualTo(2);
        }

        @Test
        @DisplayName("PRIVATE 转 PUBLIC：转公开时固化坐标")
        void privateToPublicTransferAssignsPosition() {
            Long categoryId = seedCategory("TREE_POS_TRANSFER");
            stubUserFeign();

            WishCreateResultVO created = wishService.createWish(USER_ID, new CreateWishRequest(
                    "转公开心愿", "先私密后公开", null, categoryId,
                    null, WishVisibility.PRIVATE, null, false, false, null, null));
            wishService.updateWish(USER_ID, created.id(), new UpdateWishRequest(
                    null, null, null, null, null, WishVisibility.PUBLIC, null, null, null, null));

            BigDecimal theta = jdbcTemplate.queryForObject(
                    "SELECT tree_theta FROM wish WHERE id = ?", BigDecimal.class, created.id());

            assertThat(theta).isNotNull();
        }
    }

    // ========== 聚合：上树口径 + Redis 缓存 ==========

    @Nested
    @DisplayName("聚合状态")
    class AggregationTests {

        @Test
        @DisplayName("空树：计数归零，环境默认 SUNNY")
        void emptyTreeReturnsZeroCountsAndSunny() {
            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isZero();
            assertThat(vo.totalBloom()).isZero();
            assertThat(vo.totalLight()).isZero();
            assertThat(vo.environment().name()).isEqualTo("SUNNY");
            assertThat(vo.season()).isNotNull();
        }

        @Test
        @DisplayName("聚合计数严格按上树口径：排除 PRIVATE/REJECTED/软删/OVERDUE/隐藏/无坐标")
        void aggregationCountsOnlyOnTreeWishes() {
            seedWish(101L, "PUBLIC", "ACTIVE", "BLOOM", 10, "APPROVED", 1, 0.5, 1.0, false);
            seedWish(102L, "PUBLIC", "FULFILLED", "GLOW", 3, "APPROVED", 1, 1.5, 1.0, false);
            seedWish(103L, "PUBLIC", "FULFILLING", "GLOW", 2, "APPROVED", 1, 2.5, 1.0, false);
            // 口径外干扰数据（均不应计入）
            seedWish(104L, "PRIVATE", "ACTIVE", "BLOOM", 99, "APPROVED", 1, 3.5, 1.0, false);
            seedWish(105L, "PUBLIC", "ACTIVE", "BLOOM", 99, "REJECTED", 1, 4.5, 1.0, false);
            seedWish(106L, "PUBLIC", "ACTIVE", "BLOOM", 99, "APPROVED", 1, 5.5, 1.0, true);
            seedWish(107L, "PUBLIC", "ACTIVE", "BLOOM", 99, "APPROVED", 1, null, null, false);
            seedWish(108L, "PUBLIC", "OVERDUE", "BLOOM", 99, "APPROVED", 1, 0.7, 1.0, false);
            seedWish(109L, "PUBLIC", "ACTIVE", "BLOOM", 99, "APPROVED", 0, 0.8, 1.0, false);

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(3);
            assertThat(vo.totalBloom()).isEqualTo(1);
            assertThat(vo.totalLight()).isEqualTo(15);
        }

        @Test
        @DisplayName("聚合回填 Redis 缓存（TTL 5min±30s）且二次调用命中缓存")
        void aggregationBackfillsCacheAndSecondCallHitsCache() {
            seedWish(201L, "PUBLIC", "ACTIVE", "GLOW", 5, "APPROVED", 1, 1.0, 1.0, false);

            WorldTreeVO first = worldTreeService.getTreeAggregation();
            assertThat(first.totalFruits()).isEqualTo(1);

            // 缓存断言：key 存在 + TTL 在抖动窗口内
            String cached = redisTemplate.opsForValue().get(WorldTreeServiceImpl.AGG_CACHE_KEY);
            assertThat(cached).contains("\"totalFruits\":1");
            Long ttl = redisTemplate.getExpire(WorldTreeServiceImpl.AGG_CACHE_KEY, TimeUnit.SECONDS);
            assertThat(ttl).isBetween(30L, 330L);

            // DB 变化后二次聚合仍返回缓存旧值（计数允许 ≤5 分钟延迟）
            jdbcTemplate.update("UPDATE wish SET light_count = 999 WHERE id = 201");
            WorldTreeVO second = worldTreeService.getTreeAggregation();
            assertThat(second.totalLight()).isEqualTo(first.totalLight());
        }

        @Test
        @DisplayName("FLUSHDB 后缓存 miss：重新回源计算新值")
        void cacheMissAfterFlushRecomputesFromDb() {
            seedWish(202L, "PUBLIC", "ACTIVE", "GLOW", 7, "APPROVED", 1, 2.0, 1.0, false);
            worldTreeService.getTreeAggregation();
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(1);
            assertThat(vo.totalLight()).isEqualTo(7);
        }
    }

    // ========== 果实分页：cursor + 口径 + bounds ==========

    @Nested
    @DisplayName("果实分页")
    class FruitPaginationTests {

        @Test
        @DisplayName("cursor 分页：按 id DESC，hasMore/nextCursor 语义正确")
        void cursorPaginationSemantics() {
            seedWish(301L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 1.0, 1.0, false);
            seedWish(302L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 2.0, 1.0, false);
            seedWish(303L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 3.0, 1.0, false);
            stubUserFeign();

            WorldTreeService.FruitPage firstPage = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 2));

            assertThat(firstPage.records()).extracting(TreeFruitVO::id)
                    .containsExactly(303L, 302L);
            assertThat(firstPage.hasMore()).isTrue();
            assertThat(firstPage.nextCursor()).isEqualTo("302");

            WorldTreeService.FruitPage secondPage = worldTreeService.listFruits(
                    new TreeFruitsQuery(firstPage.nextCursor(), null, null, null, null, 2));

            assertThat(secondPage.records()).extracting(TreeFruitVO::id)
                    .containsExactly(301L);
            assertThat(secondPage.hasMore()).isFalse();
            assertThat(secondPage.nextCursor()).isNull();
        }

        @Test
        @DisplayName("口径排除：PRIVATE/REJECTED/软删/OVERDUE/隐藏/无坐标不返回")
        void listFruitsExcludesOffTreeWishes() {
            seedWish(401L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 1.0, 1.0, false);
            seedWish(402L, "PRIVATE", "ACTIVE", "GLOW", 0, "APPROVED", 1, 1.2, 1.0, false);
            seedWish(403L, "PUBLIC", "ACTIVE", "GLOW", 0, "REJECTED", 1, 1.4, 1.0, false);
            seedWish(404L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 1.6, 1.0, true);
            seedWish(405L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, null, null, false);
            seedWish(406L, "PUBLIC", "OVERDUE", "GLOW", 0, "APPROVED", 1, 1.8, 1.0, false);
            seedWish(407L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 0, 2.0, 1.0, false);
            stubUserFeign();

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 20));

            assertThat(page.records()).extracting(TreeFruitVO::id)
                    .containsExactly(401L);
        }

        @Test
        @DisplayName("bounds 非环绕视口：仅返回窗口内果实（theta/phi 双过滤）")
        void boundsNonWrappingFiltersViewport() {
            seedWish(501L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 0.5, 1.0, false);
            seedWish(502L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 2.0, 1.0, false);
            seedWish(503L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 4.0, 1.0, false);
            seedWish(504L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 0.6, 2.5, false); // phi 视口外
            stubUserFeign();

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, 0.5, 1.5, 0.0, 1.0, 20));

            assertThat(page.records()).extracting(TreeFruitVO::id)
                    .containsExactly(501L);
        }

        @Test
        @DisplayName("bounds 环绕视口（minLng > maxLng）：返回跨 0/2π 经度窗口果实")
        void boundsWrappingWindowCrossesZeroMeridian() {
            seedWish(601L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 6.0, 1.0, false); // 窗口内（≥5.5 侧）
            seedWish(602L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 0.3, 1.0, false); // 窗口内（≤0.5 侧）
            seedWish(603L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 3.0, 1.0, false); // 窗口外
            stubUserFeign();

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, 0.5, 1.5, 5.5, 0.5, 20));

            assertThat(page.records()).extracting(TreeFruitVO::id)
                    .containsExactlyInAnyOrder(601L, 602L);
        }

        @Test
        @DisplayName("bounds 异常（负数/部分提供）→ 兜底全量分页不报错")
        void invalidBoundsFallsBackToFullPagination() {
            seedWish(701L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 1.0, 1.0, false);
            seedWish(702L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 4.0, 1.0, false);
            stubUserFeign();

            WorldTreeService.FruitPage negativeBounds = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, -0.5, 1.5, 0.0, 1.0, 20));
            WorldTreeService.FruitPage partialBounds = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, 0.5, null, 0.0, 1.0, 20));

            assertThat(negativeBounds.records()).hasSize(2);
            assertThat(partialBounds.records()).hasSize(2);
        }

        @Test
        @DisplayName("VO 坐标回读：theta/phi 落库 DECIMAL(9,7) 后精度一致")
        void fruitVoPositionMatchesPersistedCoordinates() {
            seedWish(801L, "PUBLIC", "ACTIVE", "GLOW", 0, "APPROVED", 1, 1.2345678, 0.9876543, false);
            stubUserFeign();

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 10));

            TreeFruitVO fruit = page.records().get(0);
            assertThat(fruit.position().theta()).isCloseTo(1.2345678, within(1e-7));
            assertThat(fruit.position().phi()).isCloseTo(0.9876543, within(1e-7));
        }
    }

    // ========== 种子辅助 ==========

    /**
     * 直接落库一条心愿行（绕过 service，精确控制口径各维度：
     * visibility/status/audit_status/is_visible/软删/坐标）。
     */
    private void seedWish(long id, String visibility, String status, String fruitType,
                          int lightCount, String auditStatus, int isVisible,
                          Double theta, Double phi, boolean deleted) {
        Long categoryId = seedCategory("TREE_CAT_" + id);
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility,
                                  status, fruit_type, light_count, audit_status, is_visible,
                                  tree_theta, tree_phi, deleted_at)
                VALUES (?, ?, '世界树果实', '集成测试种子', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, USER_ID, categoryId, visibility, status, fruitType, lightCount, auditStatus,
                isVisible, theta, phi,
                deleted ? java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)) : null);
    }
}
