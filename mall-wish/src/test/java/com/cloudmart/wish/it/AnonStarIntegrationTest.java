package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateInteractionRequest;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.InteractionListQuery;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.InteractionService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.WishCreateResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 匿名星光（ANON_STAR，Sprint 2.6）链路集成测试。
 *
 * <p>覆盖：星光扣减与流水、同一心愿唯一性（DB 存在性 + uk 兜底）、
 * 日限频 3 次（真实 Redis 计数器）、余额不足拒绝、互动列表身份脱敏、
 * 取消不退星光不释放同求占位。</p>
 */
@DisplayName("匿名星光链路集成测试")
class AnonStarIntegrationTest extends WishIntegrationTestBase {

    private static final long AUTHOR_ID = 1001L;
    private static final long ACTOR_ID = 2002L;

    @Autowired
    private WishService wishService;

    @Autowired
    private InteractionService interactionService;

    private Long wishId;

    @BeforeEach
    void setUp() {
        Long categoryId = seedCategory("IT_ANON_STAR");
        stubUserFeign();
        seedUserStat(ACTOR_ID, 100);
        seedUserStat(AUTHOR_ID, 0);
        WishCreateResultVO created = wishService.createWish(
                AUTHOR_ID,
                new CreateWishRequest("匿名星光测试心愿", "验证匿名星光链路", null, categoryId,
                        List.of("测试"), WishVisibility.PUBLIC, null, null, null));
        wishId = created.id();
    }

    @Nested
    @DisplayName("匿名星光创建")
    class CreateAnonStar {

        @Test
        @DisplayName("首次匿名星光：计数 +1、扣 5 星光且留 SPEND 流水、作者余额不变")
        void firstAnonStarSpendsStarlightAndCounts() {
            var result = interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null));

            assertThat(result.type()).isEqualTo(InteractionType.ANON_STAR);
            assertThat(result.anonStarCount()).isEqualTo(1);
            assertThat(result.starlightCost()).isEqualTo(5);

            Integer anonStarCount = jdbcTemplate.queryForObject(
                    "SELECT anon_star_count FROM wish WHERE id = ?", Integer.class, wishId);
            assertThat(anonStarCount).isEqualTo(1);

            Integer actorBalance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, ACTOR_ID);
            assertThat(actorBalance).isEqualTo(95);

            Integer authorBalance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, AUTHOR_ID);
            assertThat(authorBalance).isZero();

            Integer spendLogs = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_resource_log WHERE user_id = ? AND type = 'SPEND'",
                    Integer.class, ACTOR_ID);
            assertThat(spendLogs).isEqualTo(1);
        }

        @Test
        @DisplayName("同一心愿重复匿名星光：拒绝（WISH_ALREADY_INTERACTED），计数不变")
        void duplicateAnonStarRejected() {
            interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null));

            assertThatThrownBy(() -> interactionService.createInteraction(
                            ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_ALREADY_INTERACTED);

            Integer anonStarCount = jdbcTemplate.queryForObject(
                    "SELECT anon_star_count FROM wish WHERE id = ?", Integer.class, wishId);
            assertThat(anonStarCount).isEqualTo(1);

            Integer balance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, ACTOR_ID);
            assertThat(balance).isEqualTo(95);
        }

        @Test
        @DisplayName("星光余额不足：拒绝（WISH_STARLIGHT_INSUFFICIENT），计数不变")
        void anonStarRejectedWhenInsufficientStarlight() {
            jdbcTemplate.update(
                    "UPDATE wish_user_stat SET starlight_balance = 4 WHERE user_id = ?", ACTOR_ID);

            assertThatThrownBy(() -> interactionService.createInteraction(
                            ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT);

            Integer anonStarCount = jdbcTemplate.queryForObject(
                    "SELECT anon_star_count FROM wish WHERE id = ?", Integer.class, wishId);
            assertThat(anonStarCount).isZero();
        }

        @Test
        @DisplayName("日限频 3 次：跨心愿第 4 次拒绝（WISH_RATE_LIMITED），第 3 次成功")
        void dailyLimitBoundary() {
            Long categoryId = jdbcTemplate.queryForObject(
                    "SELECT id FROM wish_category WHERE code = 'IT_ANON_STAR'", Long.class);
            for (int i = 0; i < 2; i++) {
                WishCreateResultVO extra = wishService.createWish(
                        AUTHOR_ID,
                        new CreateWishRequest("限频心愿-" + i, "验证日限频", null, categoryId,
                                List.of("测试"), WishVisibility.PUBLIC, null, null, null));
                var result = interactionService.createInteraction(
                        ACTOR_ID, extra.id(), new CreateInteractionRequest(InteractionType.ANON_STAR, null));
                assertThat(result.starlightCost()).isEqualTo(5);
            }

            // 第 3 次（本心愿）：仍放行（3 次/日上限）
            var third = interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null));
            assertThat(third.anonStarCount()).isEqualTo(1);

            // 第 4 次：跨心愿新匿名星光被限频拒绝
            WishCreateResultVO fourth = wishService.createWish(
                    AUTHOR_ID,
                    new CreateWishRequest("限频心愿-第4次", "验证日限频拒绝", null, categoryId,
                            List.of("测试"), WishVisibility.PUBLIC, null, null, null));
            assertThatThrownBy(() -> interactionService.createInteraction(
                            ACTOR_ID, fourth.id(), new CreateInteractionRequest(InteractionType.ANON_STAR, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);

            Integer balance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, ACTOR_ID);
            assertThat(balance).isEqualTo(85);
        }
    }

    @Nested
    @DisplayName("匿名星光脱敏与取消")
    class AnonymityAndRevoke {

        @Test
        @DisplayName("互动列表：匿名项昵称显示神秘星人、userId 置空、不透出真实身份")
        void interactionListAnonymized() {
            interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null));

            var page = interactionService.listInteractions(
                    wishId, AUTHOR_ID, new InteractionListQuery(null, null, 20));

            assertThat(page.records()).hasSize(1);
            var item = page.records().get(0);
            assertThat(item.type()).isEqualTo(InteractionType.ANON_STAR);
            assertThat(item.userId()).isNull();
            assertThat(item.nickname()).isEqualTo("神秘星人");
            assertThat(item.avatar()).isNull();
        }

        @Test
        @DisplayName("取消匿名星光：计数回滚 -1、扣减的 5 星光不退还")
        void revokeAnonStarRollsBackCountWithoutRefund() {
            var created = interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null));

            interactionService.revokeInteraction(ACTOR_ID, wishId, created.id());

            Integer anonStarCount = jdbcTemplate.queryForObject(
                    "SELECT anon_star_count FROM wish WHERE id = ?", Integer.class, wishId);
            assertThat(anonStarCount).isZero();

            Integer balance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, ACTOR_ID);
            assertThat(balance).isEqualTo(95);

            // 软删后（uk_interaction_unique 仅约束未删除记录）可再次匿名星光
            var again = interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.ANON_STAR, null));
            assertThat(again.anonStarCount()).isEqualTo(1);
        }
    }
}
