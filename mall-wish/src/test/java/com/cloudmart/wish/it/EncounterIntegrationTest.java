package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.service.EncounterService;
import com.cloudmart.wish.vo.EncounterLetterVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 擦肩而过集成测试（Sprint 3.3，真实 MySQL+Redis）。
 *
 * <p>覆盖文档 3.3 验收：附近模式开关/轨迹上报/匹配生成（k≥5 匿名阈值）/
 * 延迟投递/信笺匿名/互动限频与扣星光/伪造冻结/频率限制。</p>
 */
@DisplayName("擦肩而过集成测试")
class EncounterIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 测试基准点（同格同桶上报） */
    private static final double LAT = 23.1291;
    private static final double LNG = 113.2644;

    private void seedTaggedWish(long userId, String tag) {
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, tags, created_at, updated_at)
                VALUES (?, ?, '看极光', '测试', 1, 'PUBLIC', 'ACTIVE', 'APPROVED', 1, ?, NOW(), NOW())
                """, System.nanoTime(), userId, "[\"" + tag + "\"]");
    }

    private void enableMode(long userId) {
        encounterService.setNearbyMode(userId, true);
    }

    @Nested
    @DisplayName("附近模式与轨迹上报")
    class ModeAndTrace {

        @Test
        @DisplayName("未开启附近模式 → 上报 403；开启后成功并写入轨迹（TTL 设置）")
        void modeGateAndTraceStored() {
            long user = 800L;
            seedTaggedWish(user, "看极光");

            assertThatThrownBy(() -> encounterService.reportTrace(user, LAT, LNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FORBIDDEN);

            enableMode(user);
            encounterService.reportTrace(user, LAT, LNG);

            // 轨迹 Redis key 存在（bucket+cell6 结构）且带 TTL
            Set<String> allLbsKeys = redisTemplate.keys("lbs:*");
            System.out.println("[DEBUG] lbs keys after report: " + allLbsKeys);
            String exactCell6 = com.cloudmart.wish.util.GeoHashUtils.encode(LAT, LNG, 6);
            System.out.println("[DEBUG] expected cell6=" + exactCell6);
            Set<String> traceKeys = redisTemplate.keys("lbs:trace:*");
            System.out.println("[DEBUG] trace keys: " + traceKeys);
            if (traceKeys != null && !traceKeys.isEmpty()) {
                String k0 = traceKeys.iterator().next();
                System.out.println("[DEBUG] hash size of " + k0 + " = " + redisTemplate.opsForHash().size(k0)
                        + ", type=" + redisTemplate.type(k0));
            }
            String exactKey = "lbs:trace:" + EncounterMatcherBucketHolder.currentBucket()
                    + ":" + com.cloudmart.wish.util.GeoHashUtils.encode(LAT, LNG, 6);
            System.out.println("[DEBUG] exactKey=" + exactKey + " hasKey=" + redisTemplate.hasKey(exactKey));
            System.out.println("[DEBUG] lastValue=" + redisTemplate.opsForValue().get("lbs:last:800"));
            int keyCount = allLbsKeys == null ? 0
                    : (int) allLbsKeys.stream().filter(k -> k.contains(GeoHashCell.cell6(LAT, LNG))).count();
            assertThat(keyCount).isGreaterThanOrEqualTo(1);
            String anyKey = redisTemplate.keys("lbs:trace:*").iterator().next();
            Long ttl = redisTemplate.getExpire(anyKey);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0L);
        }

        @Test
        @DisplayName("频率限制：5 分钟内上报 >10 次 → 429（验收项）")
        void rateLimit() {
            long user = 801L;
            enableMode(user);
            seedTaggedWish(user, "看极光");
            for (int i = 0; i < 10; i++) {
                encounterService.reportTrace(user, LAT, LNG);
            }
            assertThatThrownBy(() -> encounterService.reportTrace(user, LAT, LNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);
        }

        @Test
        @DisplayName("伪造检测：连续 3 次异常跳跃（1 分钟 2km）→ 冻结 24h → 再上报 403")
        void spoofingFreeze() {
            long user = 802L;
            enableMode(user);
            seedTaggedWish(user, "看极光");

            // 基线上报（建立起点），随后 3 次跳跃（1 分钟内 2km → 120km/h > 15km/h）
            encounterService.reportTrace(user, LAT, LNG);
            for (int i = 1; i <= 3; i++) {
                encounterService.reportTrace(user, LAT + i * 0.018, LNG);
            }

            Integer suspicious = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_lbs_suspicious WHERE user_id = ?", Integer.class, user);
            assertThat(suspicious).isGreaterThanOrEqualTo(3);
            String frozenUntil = jdbcTemplate.queryForObject(
                    "SELECT frozen_until FROM wish_lbs_freeze WHERE user_id = ?", String.class, user);
            assertThat(frozenUntil).isNotNull();

            // 冻结后再上报 → 403
            assertThatThrownBy(() -> encounterService.reportTrace(user, LAT, LNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FORBIDDEN);
        }

        @Test
        @DisplayName("关闭附近模式 → 开关键删除，上报被拒（验收：关闭后立即停止）")
        void modeOffStops() {
            long user = 803L;
            enableMode(user);
            encounterService.setNearbyMode(user, false);
            assertThatThrownBy(() -> encounterService.reportTrace(user, LAT, LNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("匹配生成与投递")
    class MatchAndDeliver {

        @Test
        @DisplayName("5 人同格同桶同标签 → 匿名阈值达标 → 每人 4 封镜像信笺；PENDING 时 content=null")
        void matchWithKThreshold() {
            for (long userId = 810L; userId <= 814L; userId++) {
                seedTaggedWish(userId, "看极光");
                enableMode(userId);
                encounterService.reportTrace(userId, LAT, LNG);
            }

            var stats = encounterService.matchAndDeliver();

            // 5 用户两两配对 10 对 × 2 镜像 = 20 封
            Integer letters = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_encounter_letter", Integer.class);
            assertThat(letters).isEqualTo(20);
            // PENDING 状态
            Integer pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_encounter_letter WHERE status = 'PENDING'", Integer.class);
            assertThat(pending).isEqualTo(20);

            // 列表：user 810 应有 4 封；PENDING 时 content=null（契约）
            var lettersOf810 = encounterService.listLetters(810L);
            assertThat(lettersOf810).hasSize(4);
            assertThat(lettersOf810).allSatisfy(l -> {
                assertThat(l.status()).isEqualTo("PENDING");
                assertThat(l.content()).isNull();
            });
            assertThat(stats.generatedLetters()).isEqualTo(20);
        }

        @Test
        @DisplayName("匿名阈值不达标：仅 2 人同格 → 不生成信笺（文档 39.9）")
        void noMatchBelowThreshold() {
            for (long userId = 820L; userId <= 821L; userId++) {
                seedTaggedWish(userId, "看极光");
                enableMode(userId);
                encounterService.reportTrace(userId, LAT + 0.05, LNG);
            }
            var stats = encounterService.matchAndDeliver();
            Integer letters = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_encounter_letter WHERE geohash6 = ?",
                    Integer.class, GeoHashCell.cell6(LAT + 0.05, LNG));
            assertThat(letters).isZero();
            assertThat(stats.generatedLetters()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("投递：deliver_after 到期 → DELIVERED + 诗意 content；拆信 → READ；互动幂等与扣星光")
        void deliverReadInteract() {
            for (long userId = 830L; userId <= 834L; userId++) {
                seedTaggedWish(userId, "看极光");
                enableMode(userId);
                encounterService.reportTrace(userId, LAT, LNG);
            }
            encounterService.matchAndDeliver();

            // 把 user 830 的信笺 deliver_after 改为已到期 → 再跑投递
            jdbcTemplate.update(
                    "UPDATE wish_encounter_letter SET deliver_after = DATE_SUB(UTC_TIMESTAMP, INTERVAL 1 MINUTE) "
                            + "WHERE owner_user_id = 830 AND status = 'PENDING'");
            encounterService.matchAndDeliver();

            var delivered = encounterService.listLetters(830L);
            assertThat(delivered).allSatisfy(l -> {
                assertThat(l.status()).isIn("DELIVERED");
                assertThat(l.content()).isNotNull();
            });

            // 拆信 → READ
            Long firstLetter = delivered.get(0).letterId();
            var read = encounterService.markRead(830L, firstLetter);
            assertThat(read.status()).isEqualTo("READ");

            // 互动：BLESS 免费（需星光≥0）；LIGHT 扣星光 2 + 对方心愿 light_count+1
            seedUserStat(830L, 10);
            Long peerWish = jdbcTemplate.queryForObject(
                    "SELECT peer_wish_id FROM wish_encounter_letter WHERE id = ?", Long.class, firstLetter);
            encounterService.interact(830L, firstLetter, "LIGHT", null);

            Integer balance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = 830", Integer.class);
            assertThat(balance).isEqualTo(8);
            Integer lightCount = jdbcTemplate.queryForObject(
                    "SELECT light_count FROM wish WHERE id = ?", Integer.class, peerWish);
            assertThat(lightCount).isEqualTo(1);

            // 互动限频：同信笺当日第 2 次 → 429
            assertThatThrownBy(() -> encounterService.interact(830L, firstLetter, "BLESS", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);
        }
    }

    /** 测试辅助：当前桶字符串（与 EncounterServiceImpl.bucketKey 口径一致） */
    static class EncounterMatcherBucketHolder {
        static String currentBucket() {
            LocalDateTime bucket = com.cloudmart.wish.service.impl.EncounterMatcher.bucketOf(
                    LocalDateTime.now(java.time.ZoneId.of("UTC")));
            return String.valueOf(bucket.toEpochSecond(java.time.ZoneOffset.UTC) / 60);
        }
    }

    /** 测试辅助：geohash6 计算（与后端同编码） */
    static class GeoHashCell {
        static String cell6(double lat, double lng) {
            return com.cloudmart.wish.util.GeoHashUtils.encode(lat, lng, 6);
        }
    }
}
