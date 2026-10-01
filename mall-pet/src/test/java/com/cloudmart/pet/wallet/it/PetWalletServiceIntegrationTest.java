package com.cloudmart.pet.wallet.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletLedger;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletLedgerMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
import com.cloudmart.pet.wallet.impl.PetWalletServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P01/QA33/QA34：钱包账实一致的真实 MySQL 集成测试（Testcontainers）。
 *
 * <p>测试目标不能用 mock 证明（审计边界 P01）：唯一键冲突后的事务回滚语义、
 * FOR UPDATE 行锁下的并发重放、账本与余额的最终一致。H2 与 Mockito 均无证明力。</p>
 *
 * <p>采用最小切片上下文（DataSource + Flyway + MyBatis-Plus + 钱包服务），
 * 不加载 Nacos/Sentinel/RocketMQ/Redis/Feign——它们与本任务的不变量无关。
 * credit/debit 为 MANDATORY 传播，测试用 TransactionTemplate 模拟调用方业务事务。</p>
 *
 * <p>CI（ubuntu-latest 自带 Docker）必跑；本地无 Docker 时整体跳过
 * （此时账实一致性验证 NOT RUN，属第 13 节发布门禁的已知缺口）。</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PetWalletServiceIntegrationTest.WalletItConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "spring.flyway.clean-disabled=true"
        })
@ActiveProfiles("it")
@DisplayName("P01 宠物钱包账实一致（真实 MySQL）")
class PetWalletServiceIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:9.4.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Configuration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    @MapperScan("com.cloudmart.pet.repository")
    @Import(PetWalletServiceImpl.class)
    @EnableTransactionManagement
    static class WalletItConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private PetWalletService walletService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PetWalletAccountMapper accountMapper;
    @Autowired
    private PetWalletTransactionMapper transactionMapper;
    @Autowired
    private PetWalletLedgerMapper ledgerMapper;

    private static final AtomicLong USER_SEQ = new AtomicLong(9000);

    /** 每个用例独立 userId，互不干扰；期初余额通过 EARN 事实入账。 */
    private long newUser(long openingBalance) {
        long userId = USER_SEQ.incrementAndGet();
        if (openingBalance > 0) {
            creditInTx(userId, "SEED", "seed:" + userId, openingBalance);
        }
        return userId;
    }

    private PetWalletResult creditInTx(long userId, String bizType, String bizKey, long amount) {
        return inTx(() -> walletService.credit(new PetWalletCommand(userId, null, "EARN",
                bizType, bizKey, amount, "pw_" + bizKey, "hash_" + bizKey, null, "v1", null)));
    }

    private PetWalletResult debitInTx(long userId, String bizKey, long amount, String operationId) {
        return inTx(() -> walletService.debit(new PetWalletCommand(userId, null, "SPEND",
                "PURCHASE", bizKey, amount, operationId, "hash_" + bizKey, null, "v1", null)));
    }

    /** 模拟调用方业务事务边界（credit/debit 为 MANDATORY，必须已存在事务）。 */
    private <T> T inTx(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private PetWalletCommand debitCommand(long userId, String bizKey, long amount, String operationId) {
        return new PetWalletCommand(userId, null, "SPEND", "PURCHASE", bizKey, amount,
                operationId, "hash_" + bizKey, null, "v1", null);
    }

    private long balanceOf(long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<PetWalletAccount>()
                .eq(PetWalletAccount::getUserId, userId)
                .eq(PetWalletAccount::getCurrency, "PET_COIN")).getBalance();
    }

    private List<PetWalletLedger> ledgersOf(long userId) {
        return ledgerMapper.selectList(new LambdaQueryWrapper<PetWalletLedger>()
                .eq(PetWalletLedger::getUserId, userId)
                .orderByAsc(PetWalletLedger::getAccountVersion));
    }

    private void assertAccountLedgerConsistent(long userId, long openingBalance) {
        long sumDelta = ledgersOf(userId).stream().mapToLong(PetWalletLedger::getDelta).sum();
        assertThat(balanceOf(userId)).as("余额必须等于期初 + 账本累计增量").isEqualTo(openingBalance + sumDelta);
    }

    @Test
    @DisplayName("QA33 顺序重放：同 operationId 再扣一次 → 余额只变一次，仍是一笔流水")
    void sequentialReplay_balanceChangesOnce() {
        long userId = newUser(100);

        PetWalletResult first = debitInTx(userId, "order-1", 10, "pw_op_a");
        assertThat(first.duplicate()).isFalse();
        assertThat(first.balanceAfter()).isEqualTo(90);

        PetWalletResult replay = debitInTx(userId, "order-1", 10, "pw_op_a");
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(replay.balanceAfter()).isEqualTo(90);

        assertThat(balanceOf(userId)).isEqualTo(90);
        assertThat(transactionMapper.selectCount(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getUserId, userId))).isEqualTo(1);
        assertAccountLedgerConsistent(userId, 100);
    }

    @Test
    @DisplayName("QA33 并发重放：50 线程同 operationId → 单一流水，余额不变，全部返回原结果")
    void concurrentReplay_singleFact() throws Exception {
        long userId = newUser(100);
        PetWalletResult seed = debitInTx(userId, "order-1", 10, "pw_op_a");
        assertThat(balanceOf(userId)).isEqualTo(90);

        int threads = 50;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<PetWalletResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<PetWalletResult>) () -> {
                    start.await();
                    return inTx(() -> walletService.debit(debitCommand(userId, "order-1", 10, "pw_op_a")));
                }));
            }
            start.countDown();
            for (Future<PetWalletResult> future : futures) {
                PetWalletResult result = future.get(60, TimeUnit.SECONDS);
                assertThat(result.duplicate()).as("并发重放必须命中原事实").isTrue();
                assertThat(result.transactionId()).isEqualTo(seed.transactionId());
            }
        }
        assertThat(balanceOf(userId)).isEqualTo(90);
        assertThat(transactionMapper.selectCount(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getUserId, userId))).isEqualTo(1);
        assertAccountLedgerConsistent(userId, 100);
    }

    @Test
    @DisplayName("QA33 并发扣款：50 线程各扣 10（余额 1000）→ 不超扣、账实一致、流水连续")
    void concurrentDistinctOps_noOverspend() throws Exception {
        long userId = newUser(1000);
        int threads = 50;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<PetWalletResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int seq = i;
                futures.add(pool.submit((Callable<PetWalletResult>) () -> {
                    start.await();
                    return debitInTx(userId, "order-" + seq, 10, "pw_distinct_" + seq);
                }));
            }
            start.countDown();
            for (Future<PetWalletResult> future : futures) {
                assertThat(future.get(60, TimeUnit.SECONDS).duplicate()).isFalse();
            }
        }
        assertThat(balanceOf(userId)).isEqualTo(500);
        List<PetWalletLedger> ledgers = ledgersOf(userId);
        assertThat(ledgers).hasSize(51);
        long expected = 1000;
        long prevVersion = 0;
        for (PetWalletLedger ledger : ledgers) {
            assertThat(ledger.getBalanceBefore()).isEqualTo(expected);
            expected += ledger.getDelta();
            assertThat(ledger.getBalanceAfter()).isEqualTo(expected);
            assertThat(ledger.getAccountVersion()).as("账本 accountVersion 必须连续").isEqualTo(++prevVersion);
        }
        assertAccountLedgerConsistent(userId, 1000);
    }

    @Test
    @DisplayName("QA34 同 operationId 异额：PET_OPERATION_CONFLICT，余额与流水不变")
    void sameOperationIdDifferentAmount_rejected() {
        long userId = newUser(100);
        debitInTx(userId, "order-1", 10, "pw_op_a");

        assertThatThrownBy(() -> debitInTx(userId, "order-1", 20, "pw_op_a"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_OPERATION_CONFLICT));

        assertThat(balanceOf(userId)).isEqualTo(90);
        assertThat(transactionMapper.selectCount(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getUserId, userId))).isEqualTo(1);
    }

    @Test
    @DisplayName("QA34 换键同事实：不同 operationId 同一业务事实 → 返回原结果，不产生新流水")
    void sameFactDifferentOperationId_replays() {
        long userId = newUser(100);
        PetWalletResult first = debitInTx(userId, "order-1", 10, "pw_op_a");

        PetWalletResult replay = debitInTx(userId, "order-1", 10, "pw_op_b");
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(balanceOf(userId)).isEqualTo(90);
        assertThat(transactionMapper.selectCount(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getUserId, userId))).isEqualTo(1);
    }

    @Test
    @DisplayName("QA34 余额不足后重放：返回原结果而非 PET_WALLET_INSUFFICIENT")
    void replayAfterInsufficientBalance_returnsOriginalResult() {
        long userId = newUser(100);
        PetWalletResult first = debitInTx(userId, "order-1", 90, "pw_op_a");
        assertThat(first.balanceAfter()).isEqualTo(10);

        PetWalletResult replay = debitInTx(userId, "order-1", 90, "pw_op_a");
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(balanceOf(userId)).isEqualTo(10);
    }

    @Test
    @DisplayName("QA34 冻结后重放：返回原结果，不被 PET_WALLET_FROZEN 拦截")
    void replayOnFrozenAccount_returnsOriginalResult() {
        long userId = newUser(100);
        PetWalletResult first = debitInTx(userId, "order-1", 10, "pw_op_a");

        transactionTemplate.executeWithoutResult(status -> accountMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetWalletAccount>()
                        .set(PetWalletAccount::getStatus, "FROZEN")
                        .eq(PetWalletAccount::getUserId, userId)));

        PetWalletResult replay = debitInTx(userId, "order-1", 10, "pw_op_a");
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(balanceOf(userId)).isEqualTo(90);
    }

    @Test
    @DisplayName("QA34 重复退款：第二个退款请求返回原退款结果，余额只恢复一次")
    void doubleRefund_singleRefundFact() {
        long userId = newUser(100);
        PetWalletResult spend = debitInTx(userId, "order-9", 40, "pw_spend");

        PetWalletResult refund1 = inTx(() -> walletService.refundFull(
                spend.transactionId(), "pw_refund_1", "客服退款"));
        assertThat(refund1.duplicate()).isFalse();
        assertThat(refund1.balanceAfter()).isEqualTo(100);

        PetWalletResult refund2 = inTx(() -> walletService.refundFull(
                spend.transactionId(), "pw_refund_2", "重复请求"));
        assertThat(refund2.duplicate()).isTrue();
        assertThat(refund2.transactionId()).isEqualTo(refund1.transactionId());
        assertThat(refund2.balanceAfter()).isEqualTo(100);

        assertThat(balanceOf(userId)).isEqualTo(100);
        long refundCount = transactionMapper.selectCount(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getUserId, userId)
                .eq(PetWalletTransaction::getDirection, "REFUND"));
        assertThat(refundCount).isEqualTo(1);
        assertAccountLedgerConsistent(userId, 100);
    }

    /** 跨账户 operationId 竞争任务：败方以异常返回（事务已整体回滚），胜方以结果返回。 */
    private Callable<Object> raceTask(long userId, String sharedOperationId) {
        return () -> {
            try {
                return inTx(() -> walletService.debit(debitCommand(userId, "order-race", 10, sharedOperationId)));
            } catch (BusinessException | DuplicateKeyException raceLost) {
                return raceLost;
            }
        };
    }

    @Test
    @DisplayName("QA33 跨账户 operationId 竞争：恰一事实成立，失败方余额原封不动")
    void crossAccountSameOperationId_noPartialBalanceChange() throws Exception {
        long userA = newUser(100);
        long userB = newUser(100);
        String sharedOperationId = "pw_race_" + UUID.randomUUID();

        Callable<Object> taskA = raceTask(userA, sharedOperationId);
        Callable<Object> taskB = raceTask(userB, sharedOperationId);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = pool.invokeAll(List.of(taskA, taskB));
            Object outcomeA = futures.get(0).get(60, TimeUnit.SECONDS);
            Object outcomeB = futures.get(1).get(60, TimeUnit.SECONDS);
            long successCount = List.of(outcomeA, outcomeB).stream()
                    .filter(PetWalletResult.class::isInstance).count();
            assertThat(successCount).as("两个账户竞争同一 operationId 必须恰有一方成功").isEqualTo(1);
        }

        assertThat(transactionMapper.selectCount(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getOperationId, sharedOperationId))).isEqualTo(1);
        // 失败方余额必须原封不动：两个账户合计仍为 200
        long total = balanceOf(userA) + balanceOf(userB);
        assertThat(total).isEqualTo(200);
        assertAccountLedgerConsistent(userA, 100);
        assertAccountLedgerConsistent(userB, 100);
    }

    @Test
    @DisplayName("P01 resolveDuplicate：已提交事实返回原结果；未知键返回处理中")
    void resolveDuplicate_semanticsOnRealDb() {
        long userId = newUser(100);
        PetWalletResult first = debitInTx(userId, "order-1", 10, "pw_op_a");

        PetWalletResult resolved = walletService.resolveDuplicate(debitCommand(userId, "order-1", 10, "pw_op_a"));
        assertThat(resolved.duplicate()).isTrue();
        assertThat(resolved.transactionId()).isEqualTo(first.transactionId());

        assertThatThrownBy(() -> walletService.resolveDuplicate(
                debitCommand(userId, "order-x", 10, "pw_unknown_" + UUID.randomUUID())))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_REQUEST_IN_PROGRESS));
    }
}
