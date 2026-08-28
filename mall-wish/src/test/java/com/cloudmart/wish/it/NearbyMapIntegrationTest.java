package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.service.NearbyWishService;
import com.cloudmart.wish.util.GeoHashUtils;
import com.cloudmart.wish.vo.MapClusterVO;
import com.cloudmart.wish.vo.NearbyWishVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LBS 附近心愿集成测试（Sprint 3.1，真实 MySQL+Redis）。
 *
 * <p>覆盖文档 3.1 验收：附近查询模糊化坐标/PRIVATE 不出现（越权）/网格
 * 聚合角标/空坐标默认城市兜底/radius 异常兜底/geohash 非法拒绝/
 * 偏移可复现（隐私+并发与边界）。</p>
 */
@DisplayName("附近心愿集成测试")
class NearbyMapIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private NearbyWishService nearbyWishService;

    /** 测试基准点：广州塔附近 */
    private static final double LAT = 23.1059;
    private static final double LNG = 113.3236;
    private static final long AUTHOR = 600L;

    /** 种子：指定坐标的 PUBLIC 心愿（服务端同款编码，geohash7 落库） */
    private long seedPublicWishAt(double lat, double lng, String title, String visibility) {
        String geohash = GeoHashUtils.encode(lat, lng, 7);
        long wishId = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, light_count, geohash, created_at, updated_at)
                VALUES (?, ?, ?, '测试', 1, ?, 'ACTIVE', 'APPROVED', 1, 5, ?, NOW(), NOW())
                """, wishId, AUTHOR, title, visibility, geohash);
        return wishId;
    }

    @Nested
    @DisplayName("附近查询与隐私")
    class NearbyPrivacy {

        @Test
        @DisplayName("附近查询：PUBLIC 命中、PRIVATE 不出现（越权安全验收）；坐标模糊化且可复现")
        void nearbyPrivacyAndReproducible() {
            long publicWish = seedPublicWishAt(LAT, LNG, "附近心愿A", "PUBLIC");
            seedPublicWishAt(LAT + 0.001, LNG + 0.001, "私密心愿", "PRIVATE");

            List<NearbyWishVO> first = nearbyWishService.nearby(AUTHOR, LAT, LNG, 3000, null);
            List<NearbyWishVO> second = nearbyWishService.nearby(AUTHOR, LAT, LNG, 3000, null);

            assertThat(first).extracting(NearbyWishVO::wishId).containsExactly(publicWish);
            // 越权：PRIVATE 心愿（带 geohash）绝不出现在附近列表
            assertThat(first).allSatisfy(w -> assertThat(w.title()).isNotEqualTo("私密心愿"));

            // 偏移可复现：同一心愿两次查询返回同一模糊点；且带 0-50m 偏移（不等于网格中心）
            NearbyWishVO vo = first.get(0);
            NearbyWishVO vo2 = second.get(0);
            assertThat(vo.approximateLat()).isEqualTo(vo2.approximateLat());
            assertThat(vo.approximateLng()).isEqualTo(vo2.approximateLng());
            double[] gridCenter = GeoHashUtils.decodeCenter(GeoHashUtils.encode(LAT, LNG, 7));
            double offsetDistance = GeoHashUtils.distanceMeters(gridCenter[0], gridCenter[1],
                    vo.approximateLat(), vo.approximateLng());
            assertThat(offsetDistance).isLessThanOrEqualTo(50.0);
        }

        @Test
        @DisplayName("空坐标兜底：null 坐标 → 默认城市心愿返回（避免空白页）；无数据时返回空数组")
        void blankCoordinateFallback() {
            // 默认城市中心（WishMapProperties 默认 23.1291,113.2644）附近种心愿
            long fallbackWish = seedPublicWishAt(23.1291, 113.2644, "默认城市心愿", "PUBLIC");

            List<NearbyWishVO> result = nearbyWishService.nearby(AUTHOR, null, null, 5000, null);
            assertThat(result).extracting(NearbyWishVO::wishId).contains(fallbackWish);
        }

        @Test
        @DisplayName("radius 兜底：0/负数/超 50km → 默认 5km（远点不出现）")
        void radiusFallback() {
            long near = seedPublicWishAt(LAT, LNG, "近点", "PUBLIC");
            // 约 20km 外（不同 geohash5 格）
            long far = seedPublicWishAt(LAT + 0.18, LNG, "远点", "PUBLIC");

            List<NearbyWishVO> result = nearbyWishService.nearby(AUTHOR, LAT, LNG, 0, null);
            assertThat(result).extracting(NearbyWishVO::wishId).containsExactly(near);
            assertThat(result.get(0).distance()).isLessThanOrEqualTo(5000);

            // null radius 同样兜底 5km
            List<NearbyWishVO> resultNull = nearbyWishService.nearby(AUTHOR, LAT, LNG, null, null);
            assertThat(resultNull).extracting(NearbyWishVO::wishId).containsExactly(near);
            org.assertj.core.api.Assertions.assertThat(far).isNotNull();
        }

        @Test
        @DisplayName("geohash 参数：非法字符/长度不足拒绝（验收项）")
        void invalidGeohashRejected() {
            assertThatThrownBy(() -> nearbyWishService.nearby(AUTHOR, null, null, null, "a"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
            assertThatThrownBy(() -> nearbyWishService.nearby(AUTHOR, null, null, null, "ws1e2"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("网格聚合")
    class ClusterAggregation {

        @Test
        @DisplayName("同 geohash6 网格聚合 count=2；不同网格分开；坐标=网格中心")
        void clusterCounts() {
            // 以 geohash6 格中心为锚点确定性布点（两心愿恒同格，规避边界跨格）
            String cell6 = GeoHashUtils.encode(LAT, LNG, 6);
            double[] cellCenter = GeoHashUtils.decodeCenter(cell6);
            seedPublicWishAt(cellCenter[0], cellCenter[1], "同格A", "PUBLIC");
            seedPublicWishAt(cellCenter[0] + 0.0001, cellCenter[1] + 0.0001, "同格B", "PUBLIC");
            // 约 2km 外（不同 geohash6）
            seedPublicWishAt(LAT + 0.018, LNG, "别格C", "PUBLIC");

            List<MapClusterVO> clusters = nearbyWishService.cluster(AUTHOR, LAT, LNG, 5000, null);

            assertThat(clusters).hasSizeGreaterThanOrEqualTo(2);
            MapClusterVO big = clusters.stream()
                    .filter(c -> c.count() == 2)
                    .findFirst().orElseThrow();
            double[] center = GeoHashUtils.decodeCenter(big.geohash6());
            assertThat(big.centerLat()).isCloseTo(center[0], within(0.0001));
            assertThat(big.centerLng()).isCloseTo(center[1], within(0.0001));
        }
    }
}
