package com.cloudmart.wish.it;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.SubmitFulfillmentRequest;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.FulfillmentService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishFulfillmentSubmitVO;
import com.cloudmart.wish.vo.WishFulfillmentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 还愿链路集成测试（真实 MySQL：状态流转/统计联动/星光流水/徽章判定/唯一约束）。
 *
 * <p>覆盖（文档 2.4 + 6.1 + 6.5）：提交即 FULFILLED+BLOOM、还愿星光 +50、
 * total_fulfilled/active_wishes 统计联动、FIRST_FULFILL 徽章同事务授予、
 * 重复还愿拒绝、作者级防存在性探测、还愿详情可见性。</p>
 */
@DisplayName("还愿链路集成测试")
class FulfillmentIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private WishService wishService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 2002L;

    private WishCreateResultVO createWish(Long categoryId, WishVisibility visibility) {
        return wishService.createWish(USER_ID, new CreateWishRequest(
                "集成测试心愿", "还愿链路验证", null, categoryId,
                List.of("测试"), visibility, null, null, null, null, null));
    }

    private SubmitFulfillmentRequest buildFulfillmentRequest() {
        return new SubmitFulfillmentRequest(
                "终于实现了！", List.of("oss://photo1.png"), "感恩一路相伴");
    }

    @Nested
    @DisplayName("提交还愿")
    class SubmitFulfillment {

        @Test
        @DisplayName("ACTIVE 心愿还愿成功：FULFILLED+BLOOM+星光50+统计联动+FIRST_FULFILL 徽章同事务")
        void fulfillActiveWishTransitionsAndRewards() {
            Long categoryId = seedCategory("IT_FULFILL_1");
            stubUserFeign();
            seedUserStat(USER_ID, 0);
            WishCreateResultVO wish = createWish(categoryId, WishVisibility.PUBLIC);

            WishFulfillmentSubmitVO result = fulfillmentService.submitFulfillment(
                    USER_ID, wish.id(), buildFulfillmentRequest());

            // 心愿状态流转：FULFILLED + BLOOM + fulfilled_at 落库
            assertThat(result.status()).isEqualTo(WishStatus.FULFILLED);
            assertThat(result.fruitType()).isEqualTo(FruitType.BLOOM);
            assertThat(result.starlightReward()).isEqualTo(50);
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM wish WHERE id = ?", String.class, wish.id());
            String dbFruitType = jdbcTemplate.queryForObject(
                    "SELECT fruit_type FROM wish WHERE id = ?", String.class, wish.id());
            LocalDateTime fulfilledAt = jdbcTemplate.queryForObject(
                    "SELECT fulfilled_at FROM wish WHERE id = ?", LocalDateTime.class, wish.id());
            assertThat(dbStatus).isEqualTo("FULFILLED");
            assertThat(dbFruitType).isEqualTo("BLOOM");
            assertThat(fulfilledAt).isNotNull();

            // 还愿记录落库：先发后审 PENDING + 可见
            String auditStatus = jdbcTemplate.queryForObject(
                    "SELECT audit_status FROM wish_fulfillment WHERE wish_id = ?",
                    String.class, wish.id());
            assertThat(auditStatus).isEqualTo("PENDING");

            // 统计联动：total_fulfilled +1、active_wishes -1（创建+1 → 还愿-1 = 0）
            Integer totalFulfilled = jdbcTemplate.queryForObject(
                    "SELECT total_fulfilled FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, USER_ID);
            Integer activeWishes = jdbcTemplate.queryForObject(
                    "SELECT active_wishes FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, USER_ID);
            Integer starlightBalance = jdbcTemplate.queryForObject(
                    "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                    Integer.class, USER_ID);
            assertThat(totalFulfilled).isEqualTo(1);
            assertThat(activeWishes).isZero();
            assertThat(starlightBalance).isEqualTo(50);

            // 星光流水：FULFILL 来源 +50
            Integer fulfillLogDelta = jdbcTemplate.queryForObject(
                    "SELECT delta FROM wish_resource_log WHERE user_id = ? AND source = 'FULFILL'",
                    Integer.class, USER_ID);
            assertThat(fulfillLogDelta).isEqualTo(50);

            // FIRST_FULFILL 徽章同事务授予（种子 2002 threshold=1）
            Long badgeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_user_badge WHERE user_id = ? AND badge_id = 2002",
                    Long.class, USER_ID);
            assertThat(badgeCount).isEqualTo(1);
            assertThat(result.badgeAwarded())
                    .anySatisfy(badge -> assertThat(badge.id()).isEqualTo(2002L));
        }

        @Test
        @DisplayName("OVERDUE 心愿同样可还愿（逾期补结果，不拒之门外）")
        void fulfillOverdueWishSucceeds() {
            Long categoryId = seedCategory("IT_FULFILL_2");
            stubUserFeign();
            // 直接构造 OVERDUE 状态心愿（模拟定时扫描已流转）
            WishCreateResultVO wish = createWish(categoryId, WishVisibility.PUBLIC);
            jdbcTemplate.update("UPDATE wish SET status = 'OVERDUE' WHERE id = ?", wish.id());

            WishFulfillmentSubmitVO result = fulfillmentService.submitFulfillment(
                    USER_ID, wish.id(), buildFulfillmentRequest());

            assertThat(result.status()).isEqualTo(WishStatus.FULFILLED);
            assertThat(result.fruitType()).isEqualTo(FruitType.BLOOM);
        }

        @Test
        @DisplayName("重复还愿被拒：WISH_NOT_FULFILLABLE（状态条件 UPDATE 兜底）")
        void duplicateFulfillmentRejected() {
            Long categoryId = seedCategory("IT_FULFILL_3");
            stubUserFeign();
            WishCreateResultVO wish = createWish(categoryId, WishVisibility.PUBLIC);
            fulfillmentService.submitFulfillment(USER_ID, wish.id(), buildFulfillmentRequest());

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(
                    USER_ID, wish.id(), buildFulfillmentRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FULFILLABLE);

            // 唯一约束下 wish_fulfillment 仅一条记录
            Long fulfillmentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_fulfillment WHERE wish_id = ?",
                    Long.class, wish.id());
            assertThat(fulfillmentCount).isEqualTo(1);
        }

        @Test
        @DisplayName("非作者还愿被拒：公开心愿 WISH_NOT_AUTHOR；私密心愿 WISH_NOT_FOUND 防探测")
        void nonAuthorFulfillmentRejected() {
            Long categoryId = seedCategory("IT_FULFILL_4");
            stubUserFeign();
            WishCreateResultVO publicWish = createWish(categoryId, WishVisibility.PUBLIC);
            WishCreateResultVO privateWish = createWish(categoryId, WishVisibility.PRIVATE);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(
                    OTHER_USER_ID, publicWish.id(), buildFulfillmentRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_AUTHOR);

            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(
                    OTHER_USER_ID, privateWish.id(), buildFulfillmentRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }

        @Test
        @DisplayName("故事 XSS 转义入库 + 路径穿越内容被拒")
        void storySanitizationApplied() {
            Long categoryId = seedCategory("IT_FULFILL_5");
            stubUserFeign();
            WishCreateResultVO wish = createWish(categoryId, WishVisibility.PUBLIC);

            // 路径穿越内容直接拒绝
            assertThatThrownBy(() -> fulfillmentService.submitFulfillment(
                    USER_ID, wish.id(),
                    new SubmitFulfillmentRequest("看 ../etc/passwd 的故事", null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);

            // XSS 转义入库
            fulfillmentService.submitFulfillment(USER_ID, wish.id(),
                    new SubmitFulfillmentRequest("<script>alert(1)</script>", null, "<b>感悟</b>"));
            String dbStory = jdbcTemplate.queryForObject(
                    "SELECT story FROM wish_fulfillment WHERE wish_id = ?", String.class, wish.id());
            String dbFeeling = jdbcTemplate.queryForObject(
                    "SELECT feeling FROM wish_fulfillment WHERE wish_id = ?", String.class, wish.id());
            assertThat(dbStory).doesNotContain("<script>").contains("&lt;script&gt;");
            assertThat(dbFeeling).doesNotContain("<b>").contains("&lt;b&gt;");
        }
    }

    @Nested
    @DisplayName("还愿详情")
    class GetFulfillmentDetail {

        @Test
        @DisplayName("公开心愿还愿详情匿名可见（Feign 作者信息正常）")
        void publicFulfillmentDetailVisibleAnonymously() {
            Long categoryId = seedCategory("IT_FULFILL_D1");
            stubUserFeign();
            WishCreateResultVO wish = createWish(categoryId, WishVisibility.PUBLIC);
            fulfillmentService.submitFulfillment(USER_ID, wish.id(), buildFulfillmentRequest());
            // batchGetUsers 返回真实昵称（stubUserFeign 默认空列表 → 占位）
            when(userFeignClient.batchGetUsers(anyList())).thenReturn(ApiResponse.ok(
                    List.of(Map.of("id", USER_ID, "nickname", "小星", "avatar", "a.png"))));

            WishFulfillmentVO detail = fulfillmentService.getFulfillmentDetail(wish.id(), null);

            assertThat(detail.wishId()).isEqualTo(wish.id());
            assertThat(detail.story()).isEqualTo("终于实现了！");
            assertThat(detail.mediaUrls()).containsExactly("oss://photo1.png");
            assertThat(detail.feeling()).isEqualTo("感恩一路相伴");
            assertThat(detail.authorNickname()).isEqualTo("小星");
        }

        @Test
        @DisplayName("未还愿心愿查详情：WISH_FULFILLMENT_NOT_FOUND")
        void notFulfilledWishReturnsNotFound() {
            Long categoryId = seedCategory("IT_FULFILL_D2");
            stubUserFeign();
            WishCreateResultVO wish = createWish(categoryId, WishVisibility.PUBLIC);

            assertThatThrownBy(() -> fulfillmentService.getFulfillmentDetail(wish.id(), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_FULFILLMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("私密心愿还愿详情：作者可见，非作者 404 防探测")
        void privateFulfillmentDetailVisibility() {
            Long categoryId = seedCategory("IT_FULFILL_D3");
            stubUserFeign();
            WishCreateResultVO wish = createWish(categoryId, WishVisibility.PRIVATE);
            fulfillmentService.submitFulfillment(USER_ID, wish.id(), buildFulfillmentRequest());

            WishFulfillmentVO detail = fulfillmentService.getFulfillmentDetail(wish.id(), USER_ID);
            assertThat(detail.wishId()).isEqualTo(wish.id());

            assertThatThrownBy(() -> fulfillmentService.getFulfillmentDetail(wish.id(), OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        }
    }
}
