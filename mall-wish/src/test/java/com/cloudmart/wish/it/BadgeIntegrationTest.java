package com.cloudmart.wish.it;

import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.vo.BadgeDefinitionVO;
import com.cloudmart.wish.vo.BadgeWallItemVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 徽章系统链路集成测试（真实 mysql-it：V5 迁移/种子/同步判定/幂等/聚合查询）。
 *
 * <p>覆盖：心愿创建同事务授予 FIRST_WISH、重复触发不重复授予、
 * total_helped 达标授予 HELP_100、V5 rarity 列迁移、徽章墙聚合与图鉴。</p>
 */
@DisplayName("徽章系统链路集成测试")
class BadgeIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private WishService wishService;

    @Autowired
    private UserStatService userStatService;

    @Autowired
    private BadgeService badgeService;

    private CreateWishRequest buildWishRequest(Long categoryId) {
        return new CreateWishRequest(
                "徽章集成测试心愿", "验证徽章触发链路", null, categoryId,
                List.of("测试"), WishVisibility.PUBLIC, null, null, null, null, null);
    }

    private Map<String, Object> loadUserBadgeRow(long userId, long badgeId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM wish_user_badge WHERE user_id = ? AND badge_id = ?", userId, badgeId);
    }

    @Nested
    @DisplayName("触发链路与幂等")
    class TriggerTests {

        @Test
        @DisplayName("首个心愿创建：同事务自动授予 FIRST_WISH（种子 id=2001）")
        void firstWish_autoAwarded() {
            Long categoryId = seedCategory("IT_BADGE_1");
            stubUserFeign();

            WishCreateResultVO result = wishService.createWish(1001L, buildWishRequest(categoryId));

            assertThat(result.id()).isNotNull();
            Map<String, Object> row = loadUserBadgeRow(1001L, 2001L);
            // BIGINT UNSIGNED 经 JDBC 映射为 BigInteger，断言须按数值而非严格 equals
            assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(1001L);
            assertThat(row.get("created_at")).isNotNull();
        }

        @Test
        @DisplayName("第二个心愿创建：不重复授予（uk_user_badge 幂等）")
        void secondWish_noDuplicateAward() {
            Long categoryId = seedCategory("IT_BADGE_2");
            stubUserFeign();
            wishService.createWish(1002L, buildWishRequest(categoryId));

            wishService.createWish(1002L, buildWishRequest(categoryId));

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_user_badge WHERE user_id = ? AND badge_id = 2001",
                    Integer.class, 1002L);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("total_helped 达标：incrementTotalHelped 后授予 HELP_100（种子 id=2003）")
        void helpedReachThreshold_awarded() {
            // 预置统计记录并推进到 99（避免 100 次真实互动）
            seedUserStat(1003L, 0);
            jdbcTemplate.update(
                    "UPDATE wish_user_stat SET total_helped = 99 WHERE user_id = ?", 1003L);

            userStatService.incrementTotalHelped(1003L);

            Map<String, Object> row = loadUserBadgeRow(1003L, 2003L);
            assertThat(((Number) row.get("badge_id")).longValue()).isEqualTo(2003L);
        }

        @Test
        @DisplayName("统计记录不存在：incrementTotalHelped 不授予不报错")
        void noStatRecord_noAwardNoError() {
            userStatService.incrementTotalHelped(999999L);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_user_badge WHERE user_id = 999999",
                    Integer.class);
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("聚合查询")
    class QueryTests {

        @Test
        @DisplayName("徽章墙：已获得在前含 earnedAt，未获得含 condition 与进度")
        void badgeWall_aggregation() {
            Long categoryId = seedCategory("IT_BADGE_3");
            stubUserFeign();
            wishService.createWish(1004L, buildWishRequest(categoryId));

            List<BadgeWallItemVO> wall = badgeService.getBadgeWall(1004L);

            assertThat(wall).hasSizeGreaterThanOrEqualTo(4);
            BadgeWallItemVO first = wall.get(0);
            assertThat(first.getEarned()).isTrue();
            assertThat(first.getCode()).isEqualTo("FIRST_WISH");
            assertThat(first.getEarnedAt()).isNotNull();

            BadgeWallItemVO locked = wall.stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getEarned()))
                    .findFirst().orElseThrow();
            assertThat(locked.getCondition()).isNotNull();
            assertThat(locked.getCondition().getThreshold()).isPositive();
            assertThat(locked.getProgress()).isNotNull();
            assertThat(locked.getProgress().getCurrent()).isZero();
        }

        @Test
        @DisplayName("图鉴：V5 rarity 列已迁移且种子补齐（HELP_100=EPIC，PERSIST_365=LEGENDARY）")
        void definitions_rarityFromV5Migration() {
            List<BadgeDefinitionVO> definitions = badgeService.getDefinitions();

            Map<String, BadgeDefinitionVO> byCode = definitions.stream()
                    .collect(Collectors.toMap(BadgeDefinitionVO::getCode, vo -> vo));
            assertThat(byCode.get("FIRST_WISH").getRarity()).isEqualTo("COMMON");
            assertThat(byCode.get("HELP_100").getRarity()).isEqualTo("EPIC");
            assertThat(byCode.get("PERSIST_365").getRarity()).isEqualTo("LEGENDARY");
            assertThat(byCode.get("PERSIST_365").getCondition().getType()).isEqualTo("TOTAL_CHECKIN_DAYS");
        }
    }
}
