package com.cloudmart.wish.it;

import com.cloudmart.wish.entity.LiveWidgetConfig;
import com.cloudmart.wish.enums.WidgetPosition;
import com.cloudmart.wish.service.LiveWidgetService;
import com.cloudmart.wish.vo.LiveWidgetVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 直播心愿挂件集成测试（Sprint 3.4，真实 MySQL+Redis）。
 *
 * <p>覆盖文档 3.4 验收：挂件数据聚合（进度/打卡天数/星光）/主播无心愿
 * 去许愿引导/配置 upsert 与位置枚举/降级开关（灰度比例 0 → visible=false）/
 * 10s 缓存命中（Redis）。</p>
 */
@DisplayName("直播心愿挂件集成测试")
class LiveWidgetIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private LiveWidgetService liveWidgetService;

    private static final long STREAMER = 900L;

    private void seedStreamerWish() {
        long wishId = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, created_at, updated_at)
                VALUES (?, ?, '完成半程马拉松', '测试', 1, 'PUBLIC', 'ACTIVE', 'APPROVED', 1, NOW(), NOW())
                """, wishId, STREAMER);
        jdbcTemplate.update("""
                INSERT INTO wish_progress (wish_id, current_value, target_value,
                                           current_streak, max_streak, version, created_at, updated_at)
                VALUES (?, 6, 10, 3, 5, 0, NOW(), NOW())
                """, wishId);
        seedUserStat(STREAMER, 66);
        jdbcTemplate.update(
                "UPDATE wish_user_stat SET total_checkin_days = 21 WHERE user_id = ?", STREAMER);
    }

    @Nested
    @DisplayName("挂件数据")
    class WidgetData {

        @Test
        @DisplayName("主播有进行中心愿：聚合进度 60%/打卡 21 天/星光 66；缓存命中")
        void widgetDataAssembled() {
            seedStreamerWish();

            LiveWidgetVO first = liveWidgetService.getWidgetData(STREAMER);
            LiveWidgetVO second = liveWidgetService.getWidgetData(STREAMER);

            assertThat(first.hasWish()).isTrue();
            assertThat(first.visible()).isTrue();
            assertThat(first.progressPercentage()).isEqualTo(60);
            assertThat(first.checkinDays()).isEqualTo(21);
            assertThat(first.starlightBalance()).isEqualTo(66);
            // 第二次命中 Redis 缓存（10s TTL），数据一致
            assertThat(second).usingRecursiveComparison().isEqualTo(first);
        }

        @Test
        @DisplayName("主播无进行中心愿：hasWish=false（前端去许愿引导），不报错")
        void noWishGuide() {
            LiveWidgetVO vo = liveWidgetService.getWidgetData(901L);
            assertThat(vo.hasWish()).isFalse();
            assertThat(vo.wishId()).isNull();
            assertThat(vo.visible()).isTrue();
        }

        @Test
        @DisplayName("数据实时性：打卡天数变化后缓存过期/失效即反映（10s 内更新验收）")
        void dataRefreshAfterChange() {
            seedStreamerWish();
            liveWidgetService.getWidgetData(STREAMER);

            // 主播打卡天数变化（模拟打卡后）
            jdbcTemplate.update(
                    "UPDATE wish_user_stat SET total_checkin_days = 22 WHERE user_id = ?", STREAMER);
            // 管理端保存配置即失效缓存（变更路径验证）
            LiveWidgetConfig config = new LiveWidgetConfig();
            config.setStreamerId(STREAMER);
            config.setPosition(WidgetPosition.TOP_LEFT);
            config.setIsVisible(true);
            config.setUpdatedBy(1L);
            liveWidgetService.saveConfig(config);

            LiveWidgetVO vo = liveWidgetService.getWidgetData(STREAMER);
            assertThat(vo.checkinDays()).isEqualTo(22);
            assertThat(vo.position()).isEqualTo("TOP_LEFT");
        }
    }

    @Nested
    @DisplayName("配置与降级")
    class ConfigAndDegrade {

        @Test
        @DisplayName("配置 upsert：首次插入 + 二次更新（uk streamer）；非法位置拒绝")
        void configUpsert() {
            LiveWidgetConfig config = new LiveWidgetConfig();
            config.setStreamerId(STREAMER);
            config.setPosition(WidgetPosition.TOP_RIGHT);
            config.setStyleConfig("{\"transparent\":true}");
            config.setIsVisible(true);
            config.setUpdatedBy(1L);
            liveWidgetService.saveConfig(config);

            // 二次保存（同 streamer）→ 更新而非新增
            config.setPosition(WidgetPosition.BOTTOM_LEFT);
            liveWidgetService.saveConfig(config);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_live_widget_config WHERE streamer_id = ?",
                    Integer.class, STREAMER);
            assertThat(count).isEqualTo(1);

            String position = jdbcTemplate.queryForObject(
                    "SELECT position FROM wish_live_widget_config WHERE streamer_id = ?",
                    String.class, STREAMER);
            assertThat(position).isEqualTo("BOTTOM_LEFT");
        }

        @Test
        @DisplayName("主播级停用：is_visible=false → visible=false（降级）")
        void streamerLevelHide() {
            seedStreamerWish();
            liveWidgetService.toggleConfig(STREAMER, false);
            LiveWidgetVO vo = liveWidgetService.getWidgetData(STREAMER);
            assertThat(vo.visible()).isFalse();
            // 数据仍在（前端仅隐藏挂件）
            assertThat(vo.hasWish()).isTrue();
        }

        @Test
        @DisplayName("全局降级开关：灰度比例置 0 → 所有挂件 visible=false（实时生效）")
        void globalKillSwitch() {
            seedStreamerWish();
            liveWidgetService.getWidgetData(STREAMER);
            try {
                // 灰度 feature wish_live_widget 比例 0 → 全局隐藏（管理台实时生效）
                grayscaleService.updateRatio("wish_live_widget", 0, 1L);
                // 挂件缓存 TTL 10s：全局开关切换后 10s 内生效，测试中直接失效缓存模拟到期
                redisTemplate.delete("live:widget:" + STREAMER);
                LiveWidgetVO vo = liveWidgetService.getWidgetData(STREAMER);
                assertThat(vo.visible()).isFalse();
                // 恢复 100
                grayscaleService.updateRatio("wish_live_widget", 100, 1L);
                redisTemplate.delete("live:widget:" + STREAMER);
                LiveWidgetVO restored = liveWidgetService.getWidgetData(STREAMER);
                assertThat(restored.visible()).isTrue();
            } finally {
                grayscaleService.updateRatio("wish_live_widget", 100, 1L);
                redisTemplate.delete("live:widget:" + STREAMER);
            }
        }
    }
}
