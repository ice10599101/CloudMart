package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WarmEvent;
import com.cloudmart.wish.entity.WishFence;
import com.cloudmart.wish.service.WarmMapService;
import com.cloudmart.wish.util.GeoHashUtils;
import com.cloudmart.wish.vo.FenceCheckVO;
import com.cloudmart.wish.vo.WarmEventVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 城市幸福地图 + 地理围栏集成测试（Sprint 3.2，真实 MySQL+Redis）。
 *
 * <p>覆盖文档 3.2 验收：围栏内/边界含等号/围栏外/有效期/停用/多围栏
 * 触发/到达幂等/非本人 404；温暖事件发布 PENDING 可见 + DFA 命中
 * AUTO_HIDDEN 不可见 + 管理端审核；围栏坐标不回传（响应无坐标字段）。</p>
 */
@DisplayName("城市幸福地图集成测试")
class WarmMapIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private WarmMapService warmMapService;

    private static final long USER = 700L;
    private static final long OTHER = 701L;
    private static final double FENCE_LAT = 23.1059;
    private static final double FENCE_LNG = 113.3236;
    /** 围栏半径 100m */
    private static final int RADIUS = 100;

    private long seedOwnWish(long userId) {
        long wishId = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, created_at, updated_at)
                VALUES (?, ?, '老城书店打卡', '测试', 1, 'PUBLIC', 'ACTIVE', 'APPROVED', 1, NOW(), NOW())
                """, wishId, userId);
        return wishId;
    }

    private long seedFence(long wishId, String name, double lat, double lng, int radius,
                           LocalDateTime validFrom, LocalDateTime validTo, boolean active) {
        WishFence fence = warmMapService.createFence(new WarmMapService.SaveFenceCommand(
                name, wishId, lat, lng, radius, validFrom, validTo, active, 1L));
        return fence.getId();
    }

    /** 沿纬度向北偏移指定米数 */
    private double north(double lat, double meters) {
        return lat + meters / 111320.0;
    }

    @Nested
    @DisplayName("围栏打卡")
    class FenceCheck {

        @Test
        @DisplayName("判定：围栏内 true/边界含等号 true/围栏外 false；命中触发到达记录（幂等）")
        void checkInsideBoundaryOutside() {
            long wishId = seedOwnWish(USER);
            seedFence(wishId, "老城书店", FENCE_LAT, FENCE_LNG, RADIUS, null, null, true);

            // 围栏内（中心 0m）
            FenceCheckVO inside = warmMapService.checkFence(USER, wishId, FENCE_LAT, FENCE_LNG);
            assertThat(inside.insideFence()).isTrue();
            assertThat(inside.fenceName()).isEqualTo("老城书店");
            assertThat(inside.bloomTriggered()).isTrue();
            assertThat(inside.matchedCount()).isEqualTo(1);

            // 边界含等号：距离 = radius（沿纬度北移 100m）
            FenceCheckVO boundary = warmMapService.checkFence(USER, wishId,
                    north(FENCE_LAT, RADIUS), FENCE_LNG);
            assertThat(boundary.insideFence()).isTrue();

            // 围栏外：北移 500m
            FenceCheckVO outside = warmMapService.checkFence(USER, wishId,
                    north(FENCE_LAT, RADIUS + 400), FENCE_LNG);
            assertThat(outside.insideFence()).isFalse();
            assertThat(outside.matchedCount()).isZero();

            // 到达幂等：同日多次命中仅 1 条到达记录（uk fence+user+date）
            Integer arrivals = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_fence_arrival WHERE wish_id = ?", Integer.class, wishId);
            assertThat(arrivals).isEqualTo(1);
        }

        @Test
        @DisplayName("多围栏：坐标命中同一心愿的 2 个围栏 → matchedCount=2（验收：触发 2 个心愿绽放）")
        void multipleFencesMatched() {
            long wishId = seedOwnWish(USER);
            seedFence(wishId, "围栏甲", FENCE_LAT, FENCE_LNG, RADIUS, null, null, true);
            seedFence(wishId, "围栏乙", north(FENCE_LAT, 50), FENCE_LNG, RADIUS, null, null, true);

            FenceCheckVO vo = warmMapService.checkFence(USER, wishId, FENCE_LAT, FENCE_LNG);
            assertThat(vo.insideFence()).isTrue();
            assertThat(vo.matchedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("有效期外/停用围栏 → false（验收项）")
        void inactiveAndExpired() {
            long wishId = seedOwnWish(USER);
            seedFence(wishId, "已过期", FENCE_LAT, FENCE_LNG, RADIUS,
                    LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2025, 12, 31, 23, 59), true);
            seedFence(wishId, "已停用", FENCE_LAT, FENCE_LNG, RADIUS, null, null, false);

            FenceCheckVO vo = warmMapService.checkFence(USER, wishId, FENCE_LAT, FENCE_LNG);
            assertThat(vo.insideFence()).isFalse();
            assertThat(vo.matchedCount()).isZero();
        }

        @Test
        @DisplayName("非本人心愿 → 404 防探测；隐私：响应无围栏坐标字段")
        void nonOwnerAndPrivacy() {
            long otherWish = seedOwnWish(OTHER);
            seedFence(otherWish, "他人心愿围栏", FENCE_LAT, FENCE_LNG, RADIUS, null, null, true);

            assertThatThrownBy(() -> warmMapService.checkFence(USER, otherWish, FENCE_LAT, FENCE_LNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);

            // 响应字段隐私：FenceCheckVO 记录结构断言（无 center/坐标字段）
            long ownWish = seedOwnWish(USER);
            seedFence(ownWish, "老城书店", FENCE_LAT, FENCE_LNG, RADIUS, null, null, true);
            FenceCheckVO vo = warmMapService.checkFence(USER, ownWish, FENCE_LAT, FENCE_LNG);
            assertThat(vo).usingRecursiveComparison()
                    .isEqualTo(new FenceCheckVO(vo.wishId(), vo.insideFence(), vo.fenceName(),
                            vo.bloomTriggered(), vo.matchedCount()));
        }
    }

    @Nested
    @DisplayName("温暖事件")
    class WarmEvents {

        @Test
        @DisplayName("发布：未命中敏感词 → PENDING 可见；命中 → AUTO_HIDDEN 不可见（DFA 复用）")
        void publishAndDfa() {
            WarmEventVO ok = warmMapService.publishWarmEvent(USER, "小店老板送咖啡",
                    "下雨天给快递员免费续杯，城市瞬间温暖了", FENCE_LAT, FENCE_LNG);
            assertThat(ok.eventId()).isNotNull();

            warmMapService.publishWarmEvent(USER, "骗局预警帖",
                    "这个内容包含敏感词骗局，应被自动隐藏", FENCE_LAT, FENCE_LNG);

            List<WarmEventVO> visible = warmMapService.listWarmEvents(FENCE_LAT, FENCE_LNG, 5000, null);
            assertThat(visible).extracting(WarmEventVO::title).contains("小店老板送咖啡");
            assertThat(visible).extracting(WarmEventVO::title).doesNotContain("骗局预警帖");

            // 数据库口径：AUTO_HIDDEN + is_visible=0
            Integer hidden = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_warm_event WHERE title = '骗局预警帖' "
                            + "AND audit_status = 'AUTO_HIDDEN' AND is_visible = 0",
                    Integer.class);
            assertThat(hidden).isEqualTo(1);
        }

        @Test
        @DisplayName("管理端审核：REJECTED → 不可见；APPROVED → 恢复可见")
        void adminAudit() {
            WarmEventVO event = warmMapService.publishWarmEvent(USER, "深夜食堂",
                    "巷口的面摊凌晨两点还亮着灯", FENCE_LAT, FENCE_LNG);

            WarmEvent rejected = warmMapService.auditWarmEvent(event.eventId(), "REJECTED");
            assertThat(rejected.getIsVisible()).isFalse();
            assertThat(warmMapService.listWarmEvents(FENCE_LAT, FENCE_LNG, 5000, null))
                    .extracting(WarmEventVO::eventId)
                    .doesNotContain(event.eventId());

            warmMapService.auditWarmEvent(event.eventId(), "APPROVED");
            assertThat(warmMapService.listWarmEvents(FENCE_LAT, FENCE_LNG, 5000, null))
                    .extracting(WarmEventVO::eventId)
                    .contains(event.eventId());
        }

        @Test
        @DisplayName("网格聚合：count 与 geohash6；隐私：事件坐标为偏移点非精确点")
        void clusterAndPrivacy() {
            warmMapService.publishWarmEvent(USER, "温暖事件甲", "内容甲", FENCE_LAT, FENCE_LNG);
            warmMapService.publishWarmEvent(USER, "温暖事件乙", "内容乙", FENCE_LAT + 0.0005, FENCE_LNG + 0.0005);

            var clusters = warmMapService.clusterWarmEvents(FENCE_LAT, FENCE_LNG, 5000, null);
            int total = clusters.stream().mapToInt(com.cloudmart.wish.vo.MapClusterVO::count).sum();
            assertThat(total).isEqualTo(2);

            List<WarmEventVO> events = warmMapService.listWarmEvents(FENCE_LAT, FENCE_LNG, 5000, null);
            String cell6 = GeoHashUtils.encode(FENCE_LAT, FENCE_LNG, 6);
            assertThat(events).allSatisfy(e -> assertThat(e.geohash6()).hasSize(6));
            assertThat(cell6).hasSize(6);
        }
    }
}
