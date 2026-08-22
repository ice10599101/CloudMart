package com.cloudmart.wish.it;

import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.service.AdminTreeEnvService;
import com.cloudmart.wish.service.TreeEnvService;
import com.cloudmart.wish.service.impl.TreeEnvServiceImpl;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.cloudmart.wish.vo.TreeEnvVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生命树情绪环境联动集成测试（Sprint 2.2 待办 ① + 动态环境扩展，
 * 真实 mysql-it/redis-it）。
 *
 * <p>覆盖：窗口聚合数据源过滤（scene/role/时间窗）→ 状态机 DB 流转 →
 * Redis mood 缓存 → 扫描锁互斥 → 查询降级 → 季节落库 → 特殊事件
 * 惰性过期与单活跃语义 → 环境配置种子读取。
 * 天气在 it 环境恒 SUNNY（weather.enabled 默认 false，不外呼）。</p>
 */
@DisplayName("生命树情绪环境联动集成测试")
class TreeEnvIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private TreeEnvService treeEnvService;

    @Autowired
    private AdminTreeEnvService adminTreeEnvService;

    /** 种子：树洞 AI 回复记录（sentiment -100~100 整数存储） */
    private void seedTreeHoleReply(String role, int sentimentScore, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO wish_ai_conversation (id, user_id, session_id, scene, role, content, "
                        + "sentiment_score, created_at, updated_at) "
                        + "VALUES (?, 1001, 'tree-hole-1-1001', 'TREE_HOLE', ?, ?, ?, ?, NOW())",
                System.nanoTime(), role, "回复内容", sentimentScore, createdAt);
    }

    /** 种子：BLESS 互动记录 */
    private void seedBless(LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO wish_interaction (id, wish_id, user_id, type, content, "
                        + "starlight_cost, created_at, updated_at) "
                        + "VALUES (?, 1, ?, 'BLESS', '加油', 0, ?, NOW())",
                System.nanoTime(), System.nanoTime(), createdAt);
    }

    /** 种子：预置世界树单行状态（模拟历史流转结果；season=NULL 走实时兜底） */
    private void seedState(String environment, String source,
                           LocalDateTime triggeredAt, LocalDateTime expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO wish_world_tree_state (id, environment, environment_source, "
                        + "triggered_at, expires_at, sample_count, created_at, updated_at) "
                        + "VALUES (1, ?, ?, ?, ?, 0, NOW(), NOW())",
                environment, source, triggeredAt, expiresAt);
    }

    /** 种子：特殊事件行 */
    private void seedSpecialEvent(String eventCode, String status,
                                  LocalDateTime triggeredAt, LocalDateTime expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO wish_special_event (id, event_code, title, description, status, "
                        + "triggered_by, triggered_at, expires_at, created_at, updated_at) "
                        + "VALUES (?, ?, '流星雨', '全站流星划过树冠', ?, 1, ?, ?, NOW(), NOW())",
                System.nanoTime(), eventCode, status, triggeredAt, expiresAt);
    }

    private Map<String, Object> loadStateRow() {
        return jdbcTemplate.queryForMap("SELECT * FROM wish_world_tree_state WHERE id = 1");
    }

    /** 当前季节（UTC 日期判定；期望值动态计算保证测试四季可运行） */
    private TreeSeason currentSeason() {
        return TreeSeason.from(LocalDate.now(ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("情绪聚合与环境触发")
    class MoodScanTests {

        @Test
        @DisplayName("窗口内负面情绪：SUNNY→RAIN，mood 写入 Redis，DB 状态完整")
        void negativeMood_triggersRain() {
            seedTreeHoleReply("ASSISTANT", -80, LocalDateTime.now().minusMinutes(5));
            seedTreeHoleReply("ASSISTANT", -70, LocalDateTime.now().minusMinutes(20));

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.MOOD_RAIN);
            assertThat(vo.getTriggeredAt()).isNotNull();
            assertThat(vo.getExpiresAt()).isNull();
            assertThat(vo.getMoodScore()).isLessThan(-0.6);
            assertThat(vo.getSampleCount()).isEqualTo(2);

            Map<String, Object> row = loadStateRow();
            assertThat(row.get("environment")).isEqualTo("RAIN");
            assertThat(row.get("environment_source")).isEqualTo("MOOD_RAIN");
            assertThat(row.get("last_scan_at")).isNotNull();
            assertThat((Integer) row.get("sample_count")).isEqualTo(2);

            String moodCache = redisTemplate.opsForValue().get(TreeEnvServiceImpl.MOOD_CACHE_KEY);
            assertThat(moodCache).contains("\"score\"");
        }

        @Test
        @DisplayName("数据源过滤：窗口外样本与 USER 角色记录不参与聚合")
        void onlyWindowAssistantSamples_counted() {
            seedTreeHoleReply("ASSISTANT", -90, LocalDateTime.now().minusMinutes(5));
            // 窗口外（默认窗口 60 分钟）
            seedTreeHoleReply("ASSISTANT", 90, LocalDateTime.now().minusMinutes(120));
            // USER 角色不带情绪语义
            seedTreeHoleReply("USER", -90, LocalDateTime.now().minusMinutes(5));

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getSampleCount()).isEqualTo(1);
            assertThat(vo.getMoodScore()).isEqualTo(-0.9);
        }

        @Test
        @DisplayName("无任何样本：状态行自动初始化且维持 SUNNY，mood=null")
        void noSamples_staysSunnyWithAutoInit() {
            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(vo.getMoodScore()).isNull();
            Map<String, Object> row = loadStateRow();
            assertThat(row.get("environment")).isEqualTo("SUNNY");
        }

        @Test
        @DisplayName("正面情绪：mood>+0.3 触发 RAINBOW，expires_at=15 分钟")
        void positiveMood_triggersRainbow() {
            seedTreeHoleReply("ASSISTANT", 80, LocalDateTime.now().minusMinutes(5));

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAINBOW);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.MOOD_RAINBOW);
            assertThat(vo.getExpiresAt()).isAfterOrEqualTo(LocalDateTime.now().plusMinutes(14));
        }
    }

    @Nested
    @DisplayName("BLESS 突增与状态机流转")
    class BlessBurstAndTransitionTests {

        @Test
        @DisplayName("BLESS 突增（15 分钟内 6 条 vs 前窗 2 条）：情绪低落也触发彩虹打断下雨")
        void blessBurst_interruptsRain() {
            seedState("RAIN", "MOOD_RAIN", LocalDateTime.now().minusMinutes(10), null);
            seedTreeHoleReply("ASSISTANT", -90, LocalDateTime.now().minusMinutes(5));
            for (int i = 0; i < 6; i++) {
                seedBless(LocalDateTime.now().minusMinutes(10));
            }
            for (int i = 0; i < 2; i++) {
                seedBless(LocalDateTime.now().minusMinutes(25));
            }

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAINBOW);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.BLESS_BURST_RAINBOW);
        }

        @Test
        @DisplayName("BLESS 未突增（低于最小计数）：不触发")
        void blessBelowMinCount_noRainbow() {
            for (int i = 0; i < 4; i++) {
                seedBless(LocalDateTime.now().minusMinutes(10));
            }

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
        }

        @Test
        @DisplayName("RAIN 最短持续防抖：情绪回升但未满 30 分钟维持 RAIN")
        void rainMinDuration_holdsRain() {
            seedState("RAIN", "MOOD_RAIN", LocalDateTime.now().minusMinutes(20), null);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAIN);
            // 防抖维持时 expires_at 被显式置空（RAIN 语义无过期）
            assertThat(vo.getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("RAIN 满 30 分钟且情绪回升：恢复 SUNNY（MOOD_RECOVER）")
        void rainExpired_recovers() {
            seedState("RAIN", "MOOD_RAIN", LocalDateTime.now().minusMinutes(35), null);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.MOOD_RECOVER);
        }

        @Test
        @DisplayName("RAINBOW 过期且情绪仍低：回到 RAIN，triggered_at 重置为新触发时间")
        void rainbowExpired_fallsBackToRain() {
            LocalDateTime newTriggerWindowStart = LocalDateTime.now().minusMinutes(2);
            seedState("RAINBOW", "MOOD_RAINBOW",
                    LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(5));
            seedTreeHoleReply("ASSISTANT", -90, LocalDateTime.now().minusMinutes(5));

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.MOOD_RAIN);
            assertThat(vo.getTriggeredAt()).isAfter(newTriggerWindowStart);
        }
    }

    @Nested
    @DisplayName("并发互斥与查询")
    class LockAndQueryTests {

        @Test
        @DisplayName("扫描锁被占用：跳过扫描，状态不变更、mood 缓存不写")
        void scanLockHeld_skips() {
            seedState("SUNNY", "INIT", null, null);
            seedTreeHoleReply("ASSISTANT", -90, LocalDateTime.now().minusMinutes(5));
            redisTemplate.opsForValue().set(TreeEnvServiceImpl.SCAN_LOCK_KEY,
                    "1", 60, TimeUnit.SECONDS);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            Map<String, Object> row = loadStateRow();
            assertThat(row.get("environment")).isEqualTo("SUNNY");
            assertThat(row.get("last_scan_at")).isNull();
            assertThat(redisTemplate.opsForValue().get(TreeEnvServiceImpl.MOOD_CACHE_KEY)).isNull();
        }

        @Test
        @DisplayName("getCurrentEnv：scan 后查询返回 DB 状态 + Redis mood")
        void getCurrentEnv_afterScan() {
            seedTreeHoleReply("ASSISTANT", -75, LocalDateTime.now().minusMinutes(5));

            treeEnvService.scan();
            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(vo.getMoodScore()).isEqualTo(-0.75);
            assertThat(vo.getLastScanAt()).isNotNull();
        }

        @Test
        @DisplayName("getCurrentEnv：无扫描历史时返回默认 SUNNY 且不抛异常")
        void getCurrentEnv_withoutHistory() {
            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(vo.getMoodScore()).isNull();
        }
    }

    @Nested
    @DisplayName("季节落库（Sprint 2.2）")
    class SeasonScanTests {

        @Test
        @DisplayName("scanSeason：按 UTC 日期判定季节写入 state.season 列")
        void scanSeason_persistsSeason() {
            seedState("SUNNY", "INIT", null, null);

            TreeSeason season = treeEnvService.scanSeason();

            assertThat(season).isEqualTo(currentSeason());
            Map<String, Object> row = loadStateRow();
            assertThat(row.get("season")).isEqualTo(currentSeason().name());
        }

        @Test
        @DisplayName("scanSeason：季节未变化幂等（重复扫描结果一致且不报错）")
        void scanSeason_idempotent() {
            seedState("SUNNY", "INIT", null, null);

            TreeSeason first = treeEnvService.scanSeason();
            TreeSeason second = treeEnvService.scanSeason();

            assertThat(first).isEqualTo(second).isEqualTo(currentSeason());
        }

        @Test
        @DisplayName("season=NULL 时读取实时计算兜底（Sprint 2.1 行为不变）")
        void nullSeason_fallsBackToRealtime() {
            seedState("SUNNY", "INIT", null, null);

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getSeason()).isEqualTo(currentSeason());
        }
    }

    @Nested
    @DisplayName("特殊事件（Sprint 2.2）")
    class SpecialEventTests {

        @Test
        @DisplayName("活跃事件：getCurrentEnv 返回事件且 displayEnv 为事件代码（最高优先级）")
        void activeEvent_visibleInSnapshot() {
            seedState("SUNNY", "INIT", null, null);
            seedSpecialEvent("METEOR_SHOWER", "ACTIVE",
                    LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30));

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getSpecialEvent()).isNotNull();
            assertThat(vo.getSpecialEvent().eventCode()).isEqualTo("METEOR_SHOWER");
            assertThat(vo.getDisplayEnv()).isEqualTo("METEOR_SHOWER");
        }

        @Test
        @DisplayName("已过期事件：惰性置 ENDED 且不再返回（DB 状态落 ENDED）")
        void expiredEvent_lazyEnded() {
            seedSpecialEvent("AURORA", "ACTIVE",
                    LocalDateTime.now().minusMinutes(60), LocalDateTime.now().minusMinutes(1));

            SpecialEventVO vo = treeEnvService.getActiveSpecialEvent();

            assertThat(vo).isNull();
            Integer endedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_special_event WHERE status = 'ENDED'",
                    Integer.class);
            assertThat(endedCount).isEqualTo(1);
        }

        @Test
        @DisplayName("管理端触发：旧活跃事件被结束，新事件成为唯一活跃（单活跃语义）")
        void adminTrigger_replacesActiveEvent() {
            seedSpecialEvent("STAR_NIGHT", "ACTIVE",
                    LocalDateTime.now().minusMinutes(60), null);
            var request = new com.cloudmart.wish.dto.TriggerSpecialEventRequest();
            request.setEventCode("METEOR_SHOWER");
            request.setDurationMinutes(60);

            SpecialEventVO triggered = adminTreeEnvService.triggerSpecialEvent(request, 88L);

            assertThat(triggered.status().name()).isEqualTo("ACTIVE");
            assertThat(triggered.eventCode()).isEqualTo("METEOR_SHOWER");
            Integer activeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_special_event WHERE status = 'ACTIVE'",
                    Integer.class);
            assertThat(activeCount).isEqualTo(1);
            Map<String, Object> activeRow = jdbcTemplate.queryForMap(
                    "SELECT event_code FROM wish_special_event WHERE status = 'ACTIVE'");
            assertThat(activeRow.get("event_code")).isEqualTo("METEOR_SHOWER");
        }

        @Test
        @DisplayName("管理端手动结束：事件置 ENDED，getCurrentEnv 不再展示")
        void adminEnd_hidesEvent() {
            seedState("SUNNY", "INIT", null, null);
            Long eventId = System.nanoTime();
            jdbcTemplate.update(
                    "INSERT INTO wish_special_event (id, event_code, title, status, triggered_by, "
                            + "triggered_at, created_at, updated_at) "
                            + "VALUES (?, 'STAR_NIGHT', '星辰夜', 'ACTIVE', 1, NOW(), NOW(), NOW())",
                    eventId);

            adminTreeEnvService.endSpecialEvent(eventId);

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);
            assertThat(vo.getSpecialEvent()).isNull();
            assertThat(vo.getDisplayEnv()).isEqualTo("SUNNY");
        }
    }

    @Nested
    @DisplayName("环境配置表（Sprint 2.2）")
    class EnvConfigTests {

        @Test
        @DisplayName("公开配置接口：返回 V10 全量种子（16 条，priority 降序，visual 可解析）")
        void listActiveEnvConfigs_returnsSeeds() {
            List<EnvConfigVO> configs = treeEnvService.listActiveEnvConfigs();

            assertThat(configs).hasSize(16);
            // priority 降序：特殊事件(100)在前，时段(10)在后
            assertThat(configs.get(0).priority()).isEqualTo(100);
            assertThat(configs.get(configs.size() - 1).priority()).isEqualTo(10);
            assertThat(configs.stream().map(EnvConfigVO::envCode))
                    .contains("SUNNY", "RAIN", "SPRING", "METEOR_SHOWER");
            EnvConfigVO meteor = configs.stream()
                    .filter(c -> "METEOR_SHOWER".equals(c.envCode())).findFirst().orElseThrow();
            assertThat(meteor.visual().path("particle").asText()).isEqualTo("METEOR");
        }

        @Test
        @DisplayName("下架过滤：is_active=0 的配置不出现在公开列表")
        void inactiveConfig_filtered() {
            jdbcTemplate.update(
                    "UPDATE wish_env_config SET is_active = 0 WHERE env_code = 'SUNNY'");

            List<EnvConfigVO> configs = treeEnvService.listActiveEnvConfigs();

            assertThat(configs).hasSize(15);
            assertThat(configs.stream().map(EnvConfigVO::envCode))
                    .doesNotContain("SUNNY");
        }
    }
}
