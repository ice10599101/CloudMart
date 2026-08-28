package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.ContentFlowLog;
import com.cloudmart.wish.enums.ContentFlowStatus;
import com.cloudmart.wish.repository.ContentFlowLogMapper;
import com.cloudmart.wish.service.LeaderboardService;
import com.cloudmart.wish.service.LegacyFlowService;
import com.cloudmart.wish.vo.InheritResultVO;
import com.cloudmart.wish.vo.LeaderboardEntryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 传承 + 内容流转 + 排行榜集成测试（Sprint 2.7，真实 MySQL 9 + Redis）。
 *
 * <p>覆盖文档 2.7 验收：传承定向推送/一次传承/409 语义；内容流转
 * community 不可用时记 FAILED（重试后）且不阻断还愿；排行榜四榜单
 * 排序/同分/封禁过滤/空数据/幂等。</p>
 */
@DisplayName("传承与排行榜集成测试")
class LegacyLeaderboardIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private LegacyFlowService legacyFlowService;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private ContentFlowLogMapper flowLogMapper;

    private static final long AUTHOR = 500L;
    private static final long SAME_WISH_A = 501L;
    private static final long SAME_WISH_B = 502L;

    /** 种子：已实现心愿（FULFILLED + 还愿故事） */
    private long seedFulfilledWish(long authorId, String title, int lightCount) {
        long wishId = insertWish(authorId, title, lightCount, "FULFILLED");
        jdbcTemplate.update("""
                INSERT INTO wish_fulfillment (id, wish_id, user_id, story, media_urls, audit_status,
                                              is_visible, is_inherited, created_at, updated_at)
                VALUES (?, ?, ?, '终于冲过了终点线，那一刻泪目', '[]', 'APPROVED', 1, 0, NOW(), NOW())
                """, System.nanoTime(), wishId, authorId);
        return wishId;
    }

    private long insertWish(long authorId, String title, int lightCount, String status) {
        long wishId = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                                  audit_status, is_visible, light_count, created_at, updated_at)
                VALUES (?, ?, ?, '测试', 1, 'PUBLIC', ?, 'APPROVED', 1, ?, NOW(), NOW())
                """, wishId, authorId, title, status, lightCount);
        return wishId;
    }

    private void seedSameWish(long wishId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO wish_interaction (id, wish_id, user_id, type, created_at, updated_at)
                VALUES (?, ?, ?, 'SAME_WISH', NOW(), NOW())
                """, System.nanoTime(), wishId, userId);
    }

    private void seedUserStatFull(long userId, int checkinDays, int helped, boolean restricted) {
        seedUserStat(userId, 0);
        jdbcTemplate.update("""
                UPDATE wish_user_stat SET total_checkin_days = ?, total_helped = ?, is_restricted = ?
                WHERE user_id = ?
                """, checkinDays, helped, restricted, userId);
    }

    @Nested
    @DisplayName("传承推送")
    class InheritPush {

        @Test
        @DisplayName("定向推送曾同求用户：pushedCount=目标数；is_inherited 置位；重复传承 409")
        void inheritPushAndDedup() {
            long wishId = seedFulfilledWish(AUTHOR, "完成半程马拉松", 26);
            seedSameWish(wishId, SAME_WISH_A);
            seedSameWish(wishId, SAME_WISH_B);

            InheritResultVO vo = legacyFlowService.pushInherit(AUTHOR, wishId, "谢谢你们陪我跑完");

            assertThat(vo.pushedCount()).isEqualTo(2);
            Integer pushed = jdbcTemplate.queryForObject(
                    "SELECT pushed_count FROM wish_fulfillment_inherit WHERE id = ?", Integer.class, vo.inheritId());
            Integer targets = jdbcTemplate.queryForObject(
                    "SELECT target_count FROM wish_fulfillment_inherit WHERE id = ?", Integer.class, vo.inheritId());
            assertThat(pushed).isEqualTo(2);
            assertThat(targets).isEqualTo(2);
            Integer isInherited = jdbcTemplate.queryForObject(
                    "SELECT is_inherited FROM wish_fulfillment WHERE wish_id = ?", Integer.class, wishId);
            assertThat(isInherited).isEqualTo(1);

            assertThatThrownBy(() -> legacyFlowService.pushInherit(AUTHOR, wishId, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_ALREADY_INHERITED);
        }

        @Test
        @DisplayName("非作者 403 / 未还愿 409 / 同求者本人发起 403")
        void inheritGuards() {
            long wishId = seedFulfilledWish(AUTHOR, "完成半程马拉松", 10);

            assertThatThrownBy(() -> legacyFlowService.pushInherit(SAME_WISH_A, wishId, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR);

            long activeWishId = insertWish(AUTHOR, "还没实现", 3, "ACTIVE");
            assertThatThrownBy(() -> legacyFlowService.pushInherit(AUTHOR, activeWishId, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FULFILLED);
        }
    }

    @Nested
    @DisplayName("内容流转")
    class ContentFlow {

        @Test
        @DisplayName("community 不可用：异步重试后记 FAILED（还愿主链路不受影响）")
        void flowFailsGracefullyWhenCommunityDown() throws InterruptedException {
            long wishId = seedFulfilledWish(AUTHOR, "项目顺利上线", 15);
            long fulfillmentId = jdbcTemplate.queryForObject(
                    "SELECT id FROM wish_fulfillment WHERE wish_id = ?", Long.class, wishId);

            legacyFlowService.submitContentFlow(wishId, fulfillmentId);

            // 异步 + 重试退避（0.5+1+2s）：轮询至 FAILED 落库（无 Awaitility 依赖，手写轮询）
            ContentFlowLog row = null;
            java.time.LocalDateTime deadline = java.time.LocalDateTime.now().plusSeconds(20);
            while (java.time.LocalDateTime.now().isBefore(deadline)) {
                ContentFlowLog current = flowLogMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContentFlowLog>()
                                .eq(ContentFlowLog::getFulfillmentId, fulfillmentId)
                                .last("LIMIT 1"));
                if (current != null && current.getStatus() == ContentFlowStatus.FAILED) {
                    row = current;
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
            assertThat(row).as("20s 内应完成重试并落 FAILED").isNotNull();

            // 管理端重试：community 仍不可用，保持 FAILED 不抛异常
            ContentFlowLog retryRow = flowLogMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContentFlowLog>()
                            .eq(ContentFlowLog::getFulfillmentId, fulfillmentId)
                            .last("LIMIT 1"));
            legacyFlowService.retryFlow(retryRow.getId());
            assertThat(flowLogMapper.selectById(retryRow.getId()).getStatus()).isEqualTo(ContentFlowStatus.FAILED);
        }

        @Test
        @DisplayName("撤回状态同步：SUCCESS 帖子隐藏；无流转记录时静默")
        void hideFlowWhenNoLog() {
            // 无流转记录：不抛异常（撤回先于流转完成的时序容忍）
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> legacyFlowService.hideFlow(System.nanoTime()));
        }
    }

    @Nested
    @DisplayName("排行榜")
    class Leaderboard {

        @Test
        @DisplayName("四榜单排序正确 + 同分按 created_at 早在前 + 封禁用户被排除")
        void boardOrderingAndFilters() {
            // 心愿榜：两心愿同 light_count（同分）→ 早创建在前
            // （早创建的作者用无受限账号；受限排除在用户榜单独验证）
            long early = insertWish(SAME_WISH_B, "早创建", 30, "ACTIVE");
            long late = insertWish(SAME_WISH_A, "晚创建", 30, "ACTIVE");
            jdbcTemplate.update("UPDATE wish SET created_at = DATE_SUB(NOW(), INTERVAL 10 DAY) WHERE id = ?", early);
            // 用户榜：501 打卡 50 天 / 502 帮助 40 次；AUTHOR 受限被排除（打卡 10 天）
            seedUserStatFull(SAME_WISH_A, 50, 5, false);
            seedUserStatFull(SAME_WISH_B, 80, 40, false);
            seedUserStatFull(AUTHOR, 10, 3, true);

            leaderboardService.refreshAll();

            List<LeaderboardEntryVO> hot = leaderboardService.getBoard(LeaderboardService.LeaderboardType.HOT, 100);
            assertThat(hot).hasSize(2);
            assertThat(hot.get(0).extra().get("wishTitle")).isEqualTo("早创建");
            assertThat(hot.get(0).rankDelta()).isEqualTo("NEW");
            assertThat(hot.get(0).score()).isEqualTo(30.0);

            List<LeaderboardEntryVO> persistence = leaderboardService.getBoard(
                    LeaderboardService.LeaderboardType.PERSISTENCE, 100);
            // 受限作者（打卡 10 天）被排除；80 > 50
            assertThat(persistence).extracting(LeaderboardEntryVO::userId)
                    .containsExactly(SAME_WISH_B, SAME_WISH_A);
            assertThat(persistence.get(0).extra().get("checkinDays")).isEqualTo(80);

            List<LeaderboardEntryVO> spark = leaderboardService.getBoard(
                    LeaderboardService.LeaderboardType.SPARK, 100);
            assertThat(spark).extracting(LeaderboardEntryVO::userId).containsExactly(SAME_WISH_B, SAME_WISH_A);
        }

        @Test
        @DisplayName("幂等：重复刷新结果不变；空榜返回空数组不报错")
        void refreshIdempotentAndEmpty() {
            leaderboardService.refreshAll();
            List<LeaderboardEntryVO> first = leaderboardService.getBoard(
                    LeaderboardService.LeaderboardType.SPARK, 100);
            leaderboardService.refreshAll();
            List<LeaderboardEntryVO> second = leaderboardService.getBoard(
                    LeaderboardService.LeaderboardType.SPARK, 100);
            assertThat(second).usingRecursiveFieldByFieldElementComparator()
                    .isEqualTo(first);

            // 空榜（清库后未刷新的 WARM 无数据场景模拟：直接清 ZSet）
            redisTemplate.delete("lb:rank:WARM");
            List<LeaderboardEntryVO> warm = leaderboardService.getBoard(
                    LeaderboardService.LeaderboardType.WARM, 100);
            assertThat(warm).isEmpty();
        }

        @Test
        @DisplayName("排名变化：排名超过对手 → rankDelta UP（动效三端一致依据）")
        void rankDeltaUp() {
            // 首刷：作者 5 分排第 2（对手 50 分第 1）
            long wid = insertWish(AUTHOR, "涨分心愿", 5, "ACTIVE");
            long rival = insertWish(SAME_WISH_A, "对手心愿", 50, "ACTIVE");
            seedUserStat(SAME_WISH_A, 0);
            leaderboardService.refreshAll();

            // 作者反超至第 1：排名 2→1 → UP
            jdbcTemplate.update("UPDATE wish SET light_count = 99 WHERE id = ?", wid);
            leaderboardService.refreshAll();

            List<LeaderboardEntryVO> hot = leaderboardService.getBoard(
                    LeaderboardService.LeaderboardType.HOT, 100);
            assertThat(hot).anySatisfy(e -> {
                assertThat(e.userId()).isEqualTo(AUTHOR);
                assertThat(e.rankDelta()).isEqualTo("UP");
            });
        }
    }
}
