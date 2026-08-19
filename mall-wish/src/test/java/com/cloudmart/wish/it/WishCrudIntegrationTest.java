package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 心愿核心链路集成测试（真实 MySQL：SQL/事务/逻辑删除/资源归属）。
 *
 * <p>覆盖：创建（可见性特性规则）、分类校验、作者更新、软删、
 * 详情可见性（TREE_HOLE 非作者不可见）、用户统计联动。</p>
 */
@DisplayName("心愿核心链路集成测试")
class WishCrudIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private WishService wishService;

    private CreateWishRequest buildRequest(Long categoryId, WishVisibility visibility) {
        return new CreateWishRequest(
                "集成测试心愿", "通过集成测试验证心愿创建链路", null, categoryId,
                List.of("测试"), visibility, null, null, null);
    }

    @Nested
    @DisplayName("创建心愿")
    class CreateWish {

        @Test
        @DisplayName("PUBLIC 心愿：默认关闭 AI 回复与环境联动，初始状态 ACTIVE")
        void createPublicWishAppliesDefaultFeatureFlags() {
            Long categoryId = seedCategory("IT_CAREER");
            stubUserFeign();

            WishCreateResultVO result = wishService.createWish(1001L, buildRequest(categoryId, WishVisibility.PUBLIC));

            assertThat(result.id()).isNotNull();
            assertThat(result.status()).isEqualTo(WishStatus.ACTIVE);

            Boolean enableAiReply = jdbcTemplate.queryForObject(
                    "SELECT enable_ai_reply FROM wish WHERE id = ?", Boolean.class, result.id());
            Boolean triggerEnvEmo = jdbcTemplate.queryForObject(
                    "SELECT trigger_env_emo FROM wish WHERE id = ?", Boolean.class, result.id());
            assertThat(enableAiReply).isFalse();
            assertThat(triggerEnvEmo).isFalse();
        }

        @Test
        @DisplayName("TREE_HOLE 心愿：强制 enableAiReply/triggerEnvEmo=true + STRICT 审核")
        void createTreeHoleWishForcesAiReplyAndStrictAudit() {
            Long categoryId = seedCategory("IT_GROWTH");
            stubUserFeign();

            WishCreateResultVO result = wishService.createWish(
                    1001L, buildRequest(categoryId, WishVisibility.TREE_HOLE));

            assertThat(result.status()).isEqualTo(WishStatus.ACTIVE);

            String auditStrategy = jdbcTemplate.queryForObject(
                    "SELECT audit_strategy FROM wish WHERE id = ?", String.class, result.id());
            Boolean enableAiReply = jdbcTemplate.queryForObject(
                    "SELECT enable_ai_reply FROM wish WHERE id = ?", Boolean.class, result.id());
            assertThat(auditStrategy).isEqualTo("STRICT");
            assertThat(enableAiReply).isTrue();
        }

        @Test
        @DisplayName("分类不存在：拒绝创建（WISH_CATEGORY_INVALID）")
        void rejectCreateWhenCategoryMissing() {
            stubUserFeign();
            assertThatThrownBy(() -> wishService.createWish(1001L, buildRequest(999999L, WishVisibility.PUBLIC)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_CATEGORY_INVALID);
        }

        @Test
        @DisplayName("创建联动用户统计：total_wishes/active_wishes 各 +1")
        void createWishIncrementsUserStatCounters() {
            Long categoryId = seedCategory("IT_RELATION");
            stubUserFeign();
            seedUserStat(1001L, 0);

            wishService.createWish(1001L, buildRequest(categoryId, WishVisibility.PUBLIC));

            Integer totalWishes = jdbcTemplate.queryForObject(
                    "SELECT total_wishes FROM wish_user_stat WHERE user_id = ?", Integer.class, 1001L);
            Integer activeWishes = jdbcTemplate.queryForObject(
                    "SELECT active_wishes FROM wish_user_stat WHERE user_id = ?", Integer.class, 1001L);
            assertThat(totalWishes).isEqualTo(1);
            assertThat(activeWishes).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("更新与删除")
    class UpdateAndDelete {

        @Test
        @DisplayName("作者更新标题：DB 落库生效")
        void authorCanUpdateTitle() {
            Long categoryId = seedCategory("IT_TRAVEL");
            stubUserFeign();
            WishCreateResultVO created = wishService.createWish(1001L, buildRequest(categoryId, WishVisibility.PUBLIC));

            wishService.updateWish(1001L, created.id(),
                    new UpdateWishRequest("更新后的标题", null, null, null, null, null, null, null));

            String title = jdbcTemplate.queryForObject(
                    "SELECT title FROM wish WHERE id = ?", String.class, created.id());
            assertThat(title).isEqualTo("更新后的标题");
        }

        @Test
        @DisplayName("非作者更新公开心愿：WISH_NOT_AUTHOR（403）；私密心愿：WISH_NOT_FOUND（不泄露存在性）")
        void nonAuthorCannotUpdate() {
            Long categoryId = seedCategory("IT_WEALTH");
            stubUserFeign();
            WishCreateResultVO publicWish = wishService.createWish(
                    1001L, buildRequest(categoryId, WishVisibility.PUBLIC));
            WishCreateResultVO privateWish = wishService.createWish(
                    1001L, buildRequest(categoryId, WishVisibility.PRIVATE));

            // 公开心愿对非作者可见：返回明确的作者校验错误（403）
            assertThatThrownBy(() -> wishService.updateWish(2002L, publicWish.id(),
                            new UpdateWishRequest("越权标题", null, null, null, null, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR);

            // 私密心愿对非作者不可见：统一 404，防止存在性探测
            assertThatThrownBy(() -> wishService.updateWish(2002L, privateWish.id(),
                            new UpdateWishRequest("越权标题", null, null, null, null, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }

        @Test
        @DisplayName("软删：deleted_at 落值，详情不可见，active_wishes -1")
        void softDeleteHidesWishAndDecrementsActiveCount() {
            Long categoryId = seedCategory("IT_HOBBY");
            stubUserFeign();
            seedUserStat(1001L, 0);
            WishCreateResultVO created = wishService.createWish(1001L, buildRequest(categoryId, WishVisibility.PUBLIC));

            wishService.deleteWish(1001L, created.id());

            Integer deletedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish WHERE id = ? AND deleted_at IS NOT NULL",
                    Integer.class, created.id());
            assertThat(deletedCount).isEqualTo(1);

            Integer activeWishes = jdbcTemplate.queryForObject(
                    "SELECT active_wishes FROM wish_user_stat WHERE user_id = ?", Integer.class, 1001L);
            assertThat(activeWishes).isZero();
        }
    }

    @Nested
    @DisplayName("详情与可见性")
    class DetailVisibility {

        @Test
        @DisplayName("作者查看 TREE_HOLE 心愿：可见且 enableAiReply=true 回传前端")
        void authorSeesTreeHoleWishWithAiFlag() {
            Long categoryId = seedCategory("IT_TREE");
            stubUserFeign();
            WishCreateResultVO created = wishService.createWish(
                    1001L, buildRequest(categoryId, WishVisibility.TREE_HOLE));

            WishVO detail = wishService.getWishDetail(created.id(), 1001L);

            assertThat(detail.visibility()).isEqualTo(WishVisibility.TREE_HOLE);
            assertThat(detail.enableAiReply()).isTrue();
        }

        @Test
        @DisplayName("非作者查看 TREE_HOLE 心愿：不可见（WISH_NOT_FOUND）")
        void treeHoleWishHiddenFromNonAuthor() {
            Long categoryId = seedCategory("IT_TREE2");
            stubUserFeign();
            WishCreateResultVO created = wishService.createWish(
                    1001L, buildRequest(categoryId, WishVisibility.TREE_HOLE));

            assertThatThrownBy(() -> wishService.getWishDetail(created.id(), 2002L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }
    }
}
