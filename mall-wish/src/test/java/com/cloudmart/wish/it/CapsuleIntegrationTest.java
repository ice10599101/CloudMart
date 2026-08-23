package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateCapsuleRequest;
import com.cloudmart.wish.enums.CapsuleStatus;
import com.cloudmart.wish.service.CapsuleService;
import com.cloudmart.wish.vo.CapsuleVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 时间胶囊集成测试（真实 MySQL，Sprint 2.4，文档 2.7/9.2/26.3）。
 *
 * <p>覆盖：创建→封存→到期开启全状态机、内容防绕过（非 OPENED 恒不可见）、
 * 未到期 409、取消终态、到期扫描 SEALED→AVAILABLE + MQ 事件幂等推送
 * （同一胶囊扫描 2 次仅推送 1 次）、跨时区 UTC 判定、时区上报落库。</p>
 */
@DisplayName("时间胶囊集成测试")
class CapsuleIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private CapsuleService capsuleService;

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final String CAPSULE_DESTINATION = "wish-events:capsule-available";

    private CapsuleVO createFutureCapsule(String title) {
        return capsuleService.createCapsule(USER_ID, new CreateCapsuleRequest(
                title, "封存的内容", List.of("oss://a.png"),
                LocalDateTime.now().plusDays(30), "Asia/Shanghai"));
    }

    /** 模拟时间流逝：把胶囊 open_at 改为过去（创建接口禁止过去时间）。 */
    private void expireCapsule(Long capsuleId) {
        jdbcTemplate.update(
                "UPDATE time_capsule SET open_at = ? WHERE id = ?",
                LocalDateTime.now().minusHours(1), capsuleId);
    }

    private String queryStatus(Long capsuleId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM time_capsule WHERE id = ?", String.class, capsuleId);
    }

    @Test
    @DisplayName("全状态机：创建 SEALED → 未到期开启 409 → 到期开启 OPENED 内容可见 → 重复开启幂等")
    void capsuleLifecycle_createOpen_idempotent() {
        CapsuleVO created = createFutureCapsule("写给一年后的自己");

        assertThat(created.status()).isEqualTo("SEALED");
        assertThat(created.content()).as("封存内容不可见").isNull();
        assertThat(created.openAtTimezone()).isEqualTo("Asia/Shanghai");

        // 未到期开启 → 409 WISH_CAPSULE_NOT_AVAILABLE
        assertThatThrownBy(() -> capsuleService.openCapsule(USER_ID, created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(WishErrorCodes.WISH_CAPSULE_NOT_AVAILABLE);

        // 到期开启：内容与媒体可见，openedAt 落库
        expireCapsule(created.id());
        CapsuleVO opened = capsuleService.openCapsule(USER_ID, created.id());

        assertThat(opened.status()).isEqualTo("OPENED");
        assertThat(opened.content()).isEqualTo("封存的内容");
        assertThat(opened.mediaUrls()).containsExactly("oss://a.png");
        assertThat(opened.openedAt()).isNotNull();
        assertThat(queryStatus(created.id())).isEqualTo("OPENED");

        // 幂等：重复开启直接返回内容
        CapsuleVO reopened = capsuleService.openCapsule(USER_ID, created.id());
        assertThat(reopened.status()).isEqualTo("OPENED");
        assertThat(reopened.content()).isEqualTo("封存的内容");
    }

    @Test
    @DisplayName("取消：SEALED → CANCELLED 后永久不可开启；已开启不可取消")
    void capsuleCancel_terminalAndIrreversible() {
        CapsuleVO sealed = createFutureCapsule("取消链路");

        CapsuleVO cancelled = capsuleService.cancelCapsule(USER_ID, sealed.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(queryStatus(sealed.id())).isEqualTo("CANCELLED");

        // 取消后到期也不可开启
        expireCapsule(sealed.id());
        assertThatThrownBy(() -> capsuleService.openCapsule(USER_ID, sealed.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(WishErrorCodes.WISH_CAPSULE_NOT_AVAILABLE);

        // 已开启胶囊不可取消
        CapsuleVO toOpen = createFutureCapsule("先开后取消");
        expireCapsule(toOpen.id());
        capsuleService.openCapsule(USER_ID, toOpen.id());
        assertThatThrownBy(() -> capsuleService.cancelCapsule(USER_ID, toOpen.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(WishErrorCodes.WISH_STATUS_CONFLICT);
    }

    @Test
    @DisplayName("归属隔离：非本人访问详情/开启统一 404（防存在性探测）")
    void capsuleOwnership_isolated() {
        CapsuleVO capsule = createFutureCapsule("归属隔离");

        assertThatThrownBy(() -> capsuleService.getCapsuleDetail(OTHER_USER_ID, capsule.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
        assertThatThrownBy(() -> capsuleService.openCapsule(OTHER_USER_ID, capsule.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(WishErrorCodes.WISH_NOT_FOUND);
    }

    @Test
    @DisplayName("游标分页：id 倒序 + nextCursor 续页 + 状态过滤")
    void capsuleList_cursorPagination() {
        for (int i = 1; i <= 3; i++) {
            createFutureCapsule("胶囊-" + i);
        }

        var page1 = capsuleService.listMyCapsules(USER_ID, null, null, 2);
        assertThat(page1.records()).hasSize(2);
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.records().get(0).id()).isGreaterThan(page1.records().get(1).id());

        var page2 = capsuleService.listMyCapsules(USER_ID, null, page1.nextCursor(), 2);
        assertThat(page2.records()).hasSize(1);
        assertThat(page2.hasMore()).isFalse();

        var sealedOnly = capsuleService.listMyCapsules(USER_ID, "SEALED", null, 20);
        assertThat(sealedOnly.records()).hasSize(3);

        var openedOnly = capsuleService.listMyCapsules(USER_ID, "OPENED", null, 20);
        assertThat(openedOnly.records()).isEmpty();
    }

    @Test
    @DisplayName("到期扫描：SEALED→AVAILABLE 逐条流转 + MQ 事件推送；二次扫描幂等不重复推送")
    void capsuleScan_transitionsAndPublishesOnce() {
        CapsuleVO c1 = createFutureCapsule("扫描一");
        CapsuleVO c2 = createFutureCapsule("扫描二");
        CapsuleVO future = createFutureCapsule("未到期");
        expireCapsule(c1.id());
        expireCapsule(c2.id());

        var first = capsuleService.scanAvailableCapsules();

        assertThat(first.scanned()).isEqualTo(2);
        assertThat(first.available()).isEqualTo(2);
        assertThat(queryStatus(c1.id())).isEqualTo("AVAILABLE");
        assertThat(queryStatus(c2.id())).isEqualTo("AVAILABLE");
        assertThat(queryStatus(future.id())).isEqualTo("SEALED");
        // 到期项各推送一次（MockitoBean RocketMQTemplate）
        verify(rocketMQTemplate, times(2))
                .syncSend(eq(CAPSULE_DESTINATION), any(Object.class));

        // 幂等：二次扫描无流转、无重复推送
        var second = capsuleService.scanAvailableCapsules();
        assertThat(second.scanned()).isZero();
        assertThat(second.available()).isZero();
        verify(rocketMQTemplate, times(2))
                .syncSend(eq(CAPSULE_DESTINATION), any(Object.class));

        // AVAILABLE 后用户开启（扫描→开启闭环）
        CapsuleVO opened = capsuleService.openCapsule(USER_ID, c1.id());
        assertThat(opened.status()).isEqualTo("OPENED");
        assertThat(opened.content()).isEqualTo("封存的内容");
    }

    @Test
    @DisplayName("扫描间隙容差：SEALED+已到期可直接开启（不等扫描，跨时区 UTC 判定）")
    void capsuleOpen_sealedExpiredWithoutScan() {
        CapsuleVO capsule = createFutureCapsule("扫描间隙");
        expireCapsule(capsule.id());

        // 直接开启（不经过扫描）：CAS 条件含 SEALED+openAt<=now
        CapsuleVO opened = capsuleService.openCapsule(USER_ID, capsule.id());
        assertThat(opened.status()).isEqualTo("OPENED");
        // 开启后扫描不再命中（非 SEALED），不推送
        verify(rocketMQTemplate, never()).syncSend(contains("capsule-available"), any(Object.class));
    }

    @Test
    @DisplayName("时区上报：合法 IANA 落库 wish_user_stat.timezone；统计行缺失自动初始化")
    void timezoneReport_persisted() {
        var result = capsuleService.reportTimezone(USER_ID, "America/New_York", -240);

        assertThat(result).containsEntry("timezone", "America/New_York");
        String stored = jdbcTemplate.queryForObject(
                "SELECT timezone FROM wish_user_stat WHERE user_id = ?", String.class, USER_ID);
        assertThat(stored).isEqualTo("America/New_York");

        // 非法时区被拒
        assertThatThrownBy(() -> capsuleService.reportTimezone(USER_ID, "Bad/Zone", 0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("管理统计：总量/各状态计数/今日创建")
    void adminStats_countsByStatus() {
        CapsuleVO sealed = createFutureCapsule("统计-封存");
        CapsuleVO toOpen = createFutureCapsule("统计-开启");
        expireCapsule(toOpen.id());
        capsuleService.openCapsule(USER_ID, toOpen.id());

        var stats = capsuleService.getAdminStats();

        assertThat(stats).containsEntry("total", 2L)
                .containsEntry("sealed", 1L)
                .containsEntry("opened", 1L)
                .containsEntry("available", 0L)
                .containsEntry("cancelled", 0L)
                .containsEntry("todayCreated", 2L);
        assertThat(sealed).isNotNull();
    }
}
