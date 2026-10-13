package com.cloudmart.wish.it;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.service.StarlightTransferService;
import com.cloudmart.wish.vo.StarlightTransferVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * AT12 / AT13（R28-4）星光转赠并发安全真实 MySQL+Redis 集成测试。
 *
 * <p>被测链路 {@link StarlightTransferService#transfer} 的三道并发防线（真实库验证）：
 * <ul>
 *   <li>R06 钱包行锁：双方钱包按 userId 升序 {@code SELECT ... FOR UPDATE}，
 *       余额判定在锁内——并发扣减绝不超扣；</li>
 *   <li>R06 日转出额度：权威行单语句 upsert 条件自增 {@code used_amount + amount <= 200}
 *       原子占用（R28 修复：旧 INSERT+UPDATE 两语句在首转并发下 S→X 锁升级死锁）；</li>
 *   <li>R06 持久幂等：{@code uk(from_user_id, request_key)}，同键重放稳定、异载 409。</li>
 * </ul>
 * 所有断言以数据库最终状态为准（余额、SETTLED 行数、双边流水、守恒和）。</p>
 *
 * <p>基础设施复用 {@link WishIntegrationTestBase}：Testcontainers（CI 默认）或
 * 授权环境 mysql-it(8307)/redis-it(8380) 双模式 + 跨运行互斥锁。
 * 跨服务边界（社区好友关系）以 MockitoBean 替换。</p>
 */
class StarlightTransferConcurrencyIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private StarlightTransferService starlightTransferService;

    @MockitoBean
    private CommunityFeignClient communityFeignClient;

    /** 每次运行随机高段用户号，外部共享 IT 库多运行互不冲突。 */
    private final long userBase = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
    private final AtomicLong userOffset = new AtomicLong();
    private final List<Long> testUsers = new ArrayList<>();

    /** 基类 BUSINESS_TABLES 未含 V76 新增的转赠表（既有遗漏），本类按测试用户自清理。 */
    @AfterEach
    void cleanTransferTables() {
        if (testUsers.isEmpty()) {
            return;
        }
        String in = testUsers.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM starlight_transfer WHERE from_user_id IN (" + in + ") "
                + "OR to_user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM starlight_daily_out_quota WHERE user_id IN (" + in + ")");
    }

    @BeforeEach
    void stubFriendship() {
        when(communityFeignClient.checkFriendship(anyLong(), anyLong()))
                .thenReturn(ApiResponse.ok(Map.of("friends", true)));
    }

    /**
     * AT12：余额仅够单笔（100/100），50 线程并发送礼。
     * 钱包 FOR UPDATE 串行化判定 → SETTLED 恰好 1 笔，其余 49 笔
     * WISH_STARLIGHT_INSUFFICIENT；扣款后余额精确为 0，双方总额守恒。
     */
    @Test
    void concurrent_balanceAllowsOnlyOne_singleSettlement() throws Exception {
        long sender = newUser();
        long receiver = newUser();
        seedUserStat(sender, 100);
        seedUserStat(receiver, 0);

        List<Outcome> outcomes = runConcurrently(50, () ->
                starlightTransferService.transfer(sender, receiver, 100,
                        "AT12", "at12-" + UUID.randomUUID()));

        long success = outcomes.stream().filter(Outcome::success).count();
        List<String> failCodes = outcomes.stream().filter(o -> !o.success())
                .map(Outcome::code).toList();
        assertThat(success).as("仅一笔扣款必须成功").isEqualTo(1);
        assertThat(failCodes).hasSize(49).allMatch(
                WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT::equals,
                "其余并发必须全部余额不足（不允许其他失败形态）");

        assertThat(balanceOf(sender)).as("扣款后余额精确为 0，无超扣").isZero();
        assertThat(balanceOf(receiver)).isEqualTo(100);
        assertSettledCount(sender, 1);
        assertConservation(sender, receiver, 100);
    }

    /**
     * AT13 边界：余额 20、每笔 10，50 线程并发——恰好 2 笔成功、48 笔余额不足，
     * 最终余额精确 0（永不为负）；全部成功笔同事务落双边流水与台账。
     */
    @Test
    void concurrent_exactBoundaryBalance_noOverdraft() throws Exception {
        long sender = newUser();
        long receiver = newUser();
        seedUserStat(sender, 20);
        seedUserStat(receiver, 0);

        List<Outcome> outcomes = runConcurrently(50, () ->
                starlightTransferService.transfer(sender, receiver, 10,
                        "AT13", "at13-" + UUID.randomUUID()));

        long success = outcomes.stream().filter(Outcome::success).count();
        List<String> unexpectedFailures = outcomes.stream()
                .filter(o -> !o.success())
                .map(Outcome::code)
                .filter(c -> !WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT.equals(c))
                .toList();
        assertThat(success).as("20 星光 / 单笔 10：恰好两笔成功").isEqualTo(2);
        assertThat(unexpectedFailures).as("失败只允许余额不足").isEmpty();

        assertThat(balanceOf(sender)).as("边界并发后余额精确 0，绝不超扣为负").isZero();
        assertThat(balanceOf(receiver)).isEqualTo(20);
        assertSettledCount(sender, 2);
        // 扣减+受礼同事务证据：每笔 SETTLED 双边台账 ID 齐全、credited=amount
        Long ledgerPaired = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM starlight_transfer WHERE from_user_id = ? AND status = 'SETTLED' "
                        + "AND amount = 10 AND credited_amount = 10 "
                        + "AND sender_ledger_id IS NOT NULL AND receiver_ledger_id IS NOT NULL",
                Long.class, sender);
        assertThat(ledgerPaired).isEqualTo(2L);
        assertConservation(sender, receiver, 20);
    }

    /** AT13：余额恰好等于单笔金额时正常成功；双边等额异号流水可对账。 */
    @Test
    void transfer_exactAmount_settlesWithPairedLedger() {
        long sender = newUser();
        long receiver = newUser();
        seedUserStat(sender, 30);
        seedUserStat(receiver, 100);

        StarlightTransferVO vo = starlightTransferService.transfer(
                sender, receiver, 30, null, "exact-" + UUID.randomUUID());

        assertThat(vo.status()).isEqualTo("SETTLED");
        assertThat(balanceOf(sender)).isZero();
        assertThat(balanceOf(receiver)).isEqualTo(130);
        Long logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wish_resource_log WHERE user_id IN (?, ?) AND ref_id = ? "
                        + "AND balance_after IS NOT NULL",
                Long.class, sender, receiver, vo.id());
        assertThat(logs).as("同事务必须落下转出-30/转入+30 两条流水").isEqualTo(2L);
        assertConservation(sender, receiver, 130);
    }

    /** R06 持久幂等：同键重放返回同一笔；同键异载荷 409；均不产生第二行。 */
    @Test
    void sameRequestKey_replayStable_andMismatchedPayloadRejected() {
        long sender = newUser();
        long receiver = newUser();
        seedUserStat(sender, 500);
        seedUserStat(receiver, 0);
        String key = "idem-" + UUID.randomUUID();

        StarlightTransferVO first = starlightTransferService.transfer(
                sender, receiver, 10, "first", key);
        StarlightTransferVO replay = starlightTransferService.transfer(
                sender, receiver, 10, "first", key);
        assertThat(replay.id()).isEqualTo(first.id());
        assertSettledCount(sender, 1);
        assertThat(balanceOf(sender)).isEqualTo(490);

        assertThatThrownBy(() -> starlightTransferService.transfer(
                sender, receiver, 20, "tampered", key))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(WishErrorCodes.WISH_TRANSFER_REQUEST_CONFLICT);
        assertSettledCount(sender, 1);
        assertThat(balanceOf(sender)).as("409 不得扣款").isEqualTo(490);
    }

    /** R06 日额度原子占用：第三笔（100×3 > 200/天）必须 WISH_RATE_LIMITED 整笔回滚。 */
    @Test
    void dailyCap_thirdTransferRejectedAtomically() {
        long sender = newUser();
        long receiver = newUser();
        seedUserStat(sender, 1000);
        seedUserStat(receiver, 0);

        starlightTransferService.transfer(sender, receiver, 100, null, "cap-1-" + UUID.randomUUID());
        starlightTransferService.transfer(sender, receiver, 100, null, "cap-2-" + UUID.randomUUID());
        assertThatThrownBy(() -> starlightTransferService.transfer(
                sender, receiver, 100, null, "cap-3-" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(WishErrorCodes.WISH_RATE_LIMITED);

        assertSettledCount(sender, 2);
        assertThat(balanceOf(sender)).as("被限额拒绝的第三笔不得扣款").isEqualTo(800);
        Integer used = jdbcTemplate.queryForObject(
                "SELECT used_amount FROM starlight_daily_out_quota WHERE user_id = ? "
                        + "AND business_date = CURDATE()",
                Integer.class, sender);
        assertThat(used).as("失败事务的额度占用必须回滚，权威行精确为 200").isEqualTo(200);
    }

    /** R06 守恒：接收方余量不足整笔拒绝——转出方余额不变，无任何 SETTLED 台账。 */
    @Test
    void receiverAtCapacity_wholeTransferRejected() {
        long sender = newUser();
        long receiver = newUser();
        seedUserStat(sender, 1000);
        seedUserStat(receiver, 4950);

        assertThatThrownBy(() -> starlightTransferService.transfer(
                sender, receiver, 100, null, "cap-recv-" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(WishErrorCodes.WISH_STARLIGHT_RECEIVER_CAPACITY);

        assertThat(balanceOf(sender)).as("整笔回滚，转出方不扣").isEqualTo(1000);
        assertThat(balanceOf(receiver)).isEqualTo(4950);
        assertSettledCount(sender, 0);
    }

    // ============================ 辅助 ============================

    private long newUser() {
        long id = userBase + userOffset.incrementAndGet();
        testUsers.add(id);
        return id;
    }

    private int balanceOf(long userId) {
        Integer balance = jdbcTemplate.queryForObject(
                "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?",
                Integer.class, userId);
        return balance == null ? 0 : balance;
    }

    private void assertSettledCount(long sender, long expected) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM starlight_transfer WHERE from_user_id = ? AND status = 'SETTLED'",
                Long.class, sender);
        assertThat(count).as("SETTLED 台账行数").isEqualTo(expected);
    }

    /** 双方星光总和不变（R06 守恒的数据库级断言）。 */
    private void assertConservation(long sender, long receiver, int initialTotal) {
        assertThat(balanceOf(sender) + balanceOf(receiver))
                .as("转赠前后双方星光总和必须守恒").isEqualTo(initialTotal);
    }

    /** 闸门并发：同时发起，收集成功 VO/失败错误码，不吞异常。 */
    private List<Outcome> runConcurrently(int concurrency, ThrowingTransfer action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, 30));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        ConcurrentLinkedQueue<Outcome> outcomes = new ConcurrentLinkedQueue<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        StarlightTransferVO vo = action.run();
                        outcomes.add(Outcome.ok(vo.id()));
                    } catch (BusinessException be) {
                        outcomes.add(Outcome.fail(be.getCode()));
                    } catch (Throwable t) {
                        outcomes.add(Outcome.fail(t.getClass().getSimpleName() + ":" + t.getMessage()));
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(90, TimeUnit.SECONDS)).as("并发用例必须在 90s 内全部结束").isTrue();
        } finally {
            pool.shutdownNow();
        }
        return new ArrayList<>(outcomes);
    }

    @FunctionalInterface
    private interface ThrowingTransfer {
        StarlightTransferVO run();
    }

    private record Outcome(boolean success, Long transferId, String code) {
        static Outcome ok(Long id) {
            return new Outcome(true, id, null);
        }

        static Outcome fail(String code) {
            return new Outcome(false, null, code);
        }
    }
}
