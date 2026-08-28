package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateInteractionRequest;
import com.cloudmart.wish.dto.CreateWishCommentRequest;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.InteractionService;
import com.cloudmart.wish.service.WishCommentService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.WishCommentCreateVO;
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
 * 互动/评论链路集成测试（真实 MySQL 唯一约束 + 真实 Redis 限频/幂等）。
 *
 * <p>覆盖：LIGHT 互动幂等（uk 冲突 409）、星光扣减与流水、
 * 评论创建（XSS 转义/敏感词待审标记）、评论删除。</p>
 */
@DisplayName("互动/评论链路集成测试")
class InteractionCommentIntegrationTest extends WishIntegrationTestBase {

    private static final long AUTHOR_ID = 1001L;
    private static final long ACTOR_ID = 2002L;

    @Autowired
    private WishService wishService;

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private WishCommentService wishCommentService;

    private Long wishId;

    @BeforeEach
    void setUp() {
        Long categoryId = seedCategory("IT_INTERACT");
        stubUserFeign();
        seedUserStat(ACTOR_ID, 100);
        WishCreateResultVO created = wishService.createWish(
                AUTHOR_ID,
                new CreateWishRequest("互动测试心愿", "验证互动与评论链路", null, categoryId,
                        List.of("测试"), WishVisibility.PUBLIC, null, null, null, null, null));
        wishId = created.id();
    }

    @Nested
    @DisplayName("LIGHT 点亮")
    class LightInteraction {

        @Test
        @DisplayName("首次点亮成功：计数 +1、星光 -2 且留 SPEND 流水")
        void firstLightIncrementsCountAndSpendsStarlight() {
            var result = interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.LIGHT, null));

            assertThat(result.type()).isEqualTo(InteractionType.LIGHT);
            assertThat(result.lightCount()).isEqualTo(1);
            assertThat(result.starlightCost()).isEqualTo(2);

            Integer balance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, ACTOR_ID);
            assertThat(balance).isEqualTo(98);

            Integer spendLogs = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_resource_log WHERE user_id = ? AND type = 'SPEND'",
                    Integer.class, ACTOR_ID);
            assertThat(spendLogs).isEqualTo(1);
        }

        @Test
        @DisplayName("重复点亮：产品设计允许多次（uk 仅约束非 LIGHT 类型），计数与扣费逐次累加")
        void repeatLightAccumulatesCountAndCost() {
            interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.LIGHT, null));

            var second = interactionService.createInteraction(
                    ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.LIGHT, null));

            assertThat(second.lightCount()).isEqualTo(2);
            assertThat(second.starlightCost()).isEqualTo(2);

            Integer lightCount = jdbcTemplate.queryForObject(
                    "SELECT light_count FROM wish WHERE id = ?", Integer.class, wishId);
            assertThat(lightCount).isEqualTo(2);

            Integer balance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, ACTOR_ID);
            assertThat(balance).isEqualTo(96);

            Integer spendLogs = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_resource_log WHERE user_id = ? AND type = 'SPEND'",
                    Integer.class, ACTOR_ID);
            assertThat(spendLogs).isEqualTo(2);
        }

        @Test
        @DisplayName("星光余额不足：拒绝点亮（WISH_STARLIGHT_INSUFFICIENT），计数不变")
        void lightRejectedWhenStarlightInsufficient() {
            jdbcTemplate.update(
                    "UPDATE wish_user_stat SET starlight_balance = 1 WHERE user_id = ?", ACTOR_ID);

            assertThatThrownBy(() -> interactionService.createInteraction(
                            ACTOR_ID, wishId, new CreateInteractionRequest(InteractionType.LIGHT, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT);

            Integer lightCount = jdbcTemplate.queryForObject(
                    "SELECT light_count FROM wish WHERE id = ?", Integer.class, wishId);
            assertThat(lightCount).isZero();
        }
    }

    @Nested
    @DisplayName("评论")
    class Comments {

        @Test
        @DisplayName("正常评论：内容落库且 XSS 转义生效")
        void createCommentPersistsEscapedContent() {
            WishCommentCreateVO result = wishCommentService.createComment(
                    ACTOR_ID, wishId, new CreateWishCommentRequest("<script>alert(1)</script>你好", null));

            assertThat(result.id()).isNotNull();

            String content = jdbcTemplate.queryForObject(
                    "SELECT content FROM wish_comment WHERE id = ?", String.class, result.id());
            assertThat(content).doesNotContain("<script>");
            assertThat(content).contains("你好");
        }

        @Test
        @DisplayName("敏感词评论：先发后审，仅标记 sensitive_hit=1 不阻断")
        void sensitiveCommentMarkedPending() {
            WishCommentCreateVO result = wishCommentService.createComment(
                    ACTOR_ID, wishId, new CreateWishCommentRequest("这个愿望是骗局，全是诈骗套路", null));

            Boolean sensitiveHit = jdbcTemplate.queryForObject(
                    "SELECT sensitive_hit FROM wish_comment WHERE id = ?", Boolean.class, result.id());
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM wish_comment WHERE id = ?", String.class, result.id());
            assertThat(sensitiveHit).isTrue();
            assertThat(status).isEqualTo("VISIBLE");
        }

        @Test
        @DisplayName("作者删除评论：软删生效，列表不再返回")
        void authorDeleteCommentSoftDeletes() {
            WishCommentCreateVO created = wishCommentService.createComment(
                    ACTOR_ID, wishId, new CreateWishCommentRequest("待删除的评论", null));

            wishCommentService.deleteComment(ACTOR_ID, wishId, created.id());

            Integer deleted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_comment WHERE id = ? AND deleted_at IS NOT NULL",
                    Integer.class, created.id());
            assertThat(deleted).isEqualTo(1);

            var page = wishCommentService.listComments(wishId, ACTOR_ID, null, 20);
            assertThat(page.records()).noneMatch(item -> item.id().equals(created.id()));
        }
    }
}
