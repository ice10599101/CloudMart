package com.cloudmart.wish.it;

import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.SubmitFulfillmentRequest;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.FulfillmentService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishFulfillmentSubmitVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OVERDUE 状态机定时扫描集成测试（真实 MySQL，文档 1.2 每日 00:30 扫描）。
 *
 * <p>覆盖：ACTIVE+expected_at 过期 → OVERDUE、未过期/无期望日/软删/已终态不流转、
 * 扫描幂等、OVERDUE 心愿仍可还愿（与还愿链路衔接的状态机闭环）。</p>
 */
@DisplayName("OVERDUE 状态机扫描集成测试")
class OverdueScanIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private WishService wishService;

    @Autowired
    private FulfillmentService fulfillmentService;

    private static final Long USER_ID = 1001L;

    private WishCreateResultVO createWishWithExpectedAt(Long categoryId, LocalDateTime expectedAt) {
        return wishService.createWish(USER_ID, new CreateWishRequest(
                "过期扫描测试心愿", "OVERDUE 状态机验证", null, categoryId,
                List.of("测试"), WishVisibility.PUBLIC, expectedAt, null, null));
    }

    private String queryStatus(Long wishId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM wish WHERE id = ?", String.class, wishId);
    }

    @Test
    @DisplayName("ACTIVE+期望日已过 → 扫描后流转 OVERDUE；未过期/无期望日的不流转")
    void scanTransfersExpiredActiveWishesOnly() {
        Long categoryId = seedCategory("IT_OVERDUE_1");
        stubUserFeign();
        WishCreateResultVO expiredWish = createWishWithExpectedAt(
                categoryId, LocalDateTime.now().minusDays(1));
        WishCreateResultVO futureWish = createWishWithExpectedAt(
                categoryId, LocalDateTime.now().plusMonths(1));
        WishCreateResultVO noDeadlineWish = createWishWithExpectedAt(categoryId, null);

        int transferred = wishService.scanOverdueWishes();

        assertThat(transferred).isEqualTo(1);
        assertThat(queryStatus(expiredWish.id())).isEqualTo("OVERDUE");
        assertThat(queryStatus(futureWish.id())).isEqualTo("ACTIVE");
        assertThat(queryStatus(noDeadlineWish.id())).isEqualTo("ACTIVE");

        // 幂等：二次扫描无新增（已 OVERDUE 不再命中 ACTIVE 条件）
        assertThat(wishService.scanOverdueWishes()).isZero();
    }

    @Test
    @DisplayName("软删心愿即使期望日已过也不流转")
    void softDeletedWishNotTransferred() {
        Long categoryId = seedCategory("IT_OVERDUE_2");
        stubUserFeign();
        WishCreateResultVO deletedWish = createWishWithExpectedAt(
                categoryId, LocalDateTime.now().minusDays(1));
        wishService.deleteWish(USER_ID, deletedWish.id());

        int transferred = wishService.scanOverdueWishes();

        assertThat(transferred).isZero();
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM wish WHERE id = ? AND deleted_at IS NOT NULL",
                String.class, deletedWish.id());
        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("FULFILLED 终态心愿不参与流转（期望日已过仍保持 FULFILLED）")
    void fulfilledWishNotTransferred() {
        Long categoryId = seedCategory("IT_OVERDUE_3");
        stubUserFeign();
        WishCreateResultVO wish = createWishWithExpectedAt(
                categoryId, LocalDateTime.now().minusDays(1));
        fulfillmentService.submitFulfillment(USER_ID, wish.id(),
                new SubmitFulfillmentRequest("赶在过期前完成了", null, null));

        int transferred = wishService.scanOverdueWishes();

        assertThat(transferred).isZero();
        assertThat(queryStatus(wish.id())).isEqualTo("FULFILLED");
    }

    @Test
    @DisplayName("OVERDUE 心愿仍可还愿：逾期补结果流转 FULFILLED+BLOOM（状态机闭环）")
    void overdueWishCanStillBeFulfilled() {
        Long categoryId = seedCategory("IT_OVERDUE_4");
        stubUserFeign();
        seedUserStat(USER_ID, 0);
        WishCreateResultVO wish = createWishWithExpectedAt(
                categoryId, LocalDateTime.now().minusDays(1));

        wishService.scanOverdueWishes();
        assertThat(queryStatus(wish.id())).isEqualTo("OVERDUE");

        WishFulfillmentSubmitVO result = fulfillmentService.submitFulfillment(
                USER_ID, wish.id(),
                new SubmitFulfillmentRequest("虽然逾期但实现了", null, null));

        assertThat(result.status()).isEqualTo(WishStatus.FULFILLED);
        assertThat(result.fruitType()).isEqualTo(FruitType.BLOOM);
        assertThat(queryStatus(wish.id())).isEqualTo("FULFILLED");
        Integer starlightBalance = jdbcTemplate.queryForObject(
                "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                Integer.class, USER_ID);
        assertThat(starlightBalance).isEqualTo(50);
    }
}
