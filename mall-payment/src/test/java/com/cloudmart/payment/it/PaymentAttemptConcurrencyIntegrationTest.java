package com.cloudmart.payment.it;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.channel.ChannelRegistry;
import com.cloudmart.payment.channel.PaymentChannelCatalog;
import com.cloudmart.payment.channel.PaymentScene;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import com.cloudmart.payment.repository.PaymentNotifyLogMapper;
import com.cloudmart.payment.service.PaymentAttemptService;
import com.cloudmart.payment.service.PaymentChannelQueryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * AT18（R28-4）支付尝试并发安全真实数据库集成测试。
 *
 * <p>验证 R09「同一订单最多一笔活动支付尝试」在真实 MySQL 9 上的两层防线：
 * <ul>
 *   <li>服务层 {@code GET_LOCK('payment_attempt_order_&lt;orderId&gt;', 5)} 命名锁串行化；</li>
 *   <li>数据库层 {@code uk_attempt_active_order}（PENDING 行生成列）与
 *       {@code uk_attempt_request_key} 唯一索引硬约束。</li>
 * </ul>
 * 断言基于<b>数据库最终状态</b>而非只看返回值：并发结束后同订单 PENDING 行恰好 1 笔。</p>
 *
 * <h3>基础设施双模式（与 mall-wish IT 一致）</h3>
 * <ul>
 *   <li>默认：Testcontainers 自起 mysql:9.4.0（CI runner 自带 Docker，不依赖私人机器）；</li>
 *   <li>外部模式：设置 {@code PAYMENT_IT_MYSQL_HOST} 直连授权环境专用 mysql-it(8307)，
 *       schema {@code mall_payment_it} 与业务库隔离；须 {@code -Dpayment.it.enabled=true}
 *             显式授权。用例使用每次运行随机高段 orderId，不清理共享库，多次运行互不冲突。</li>
 * </ul>
 *
 * <p>外部依赖边界：OrderFeignClient（跨服务）以 MockitoBean 桩替换，归属/金额由桩给定；
 * 不启动 Web/Nacos/RocketMQ。</p>
 */
@SpringBootTest(
        classes = PaymentAttemptConcurrencyIntegrationTest.ItSlice.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.eager=false",
                // IT 环境显式启用 MOCK 渠道（生产 fail-closed 由 ChannelRegistry 自身保证）
                "payment.mock-channel-enabled=true"
        })
@ActiveProfiles("it")
class PaymentAttemptConcurrencyIntegrationTest {

    private static final boolean USE_EXTERNAL_INFRA = System.getenv("PAYMENT_IT_MYSQL_HOST") != null;

    static final MySQLContainer<?> MYSQL = USE_EXTERNAL_INFRA ? null
            : new MySQLContainer<>("mysql:9.4.0").withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_INFRA) {
            // 外部模式：连接信息来自 application-it.yml 的 PAYMENT_IT_* 环境变量
            return;
        }
        MYSQL.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeAll
    static void requireExplicitExternalEnablement() {
        if (!USE_EXTERNAL_INFRA) {
            return;
        }
        String enabled = System.getProperty("payment.it.enabled", System.getenv("PAYMENT_IT_ENABLED"));
        if (!"true".equals(enabled)) {
            throw new IllegalStateException(
                    "IT 未显式启用：外部共享 IT 库模式须以 -Dpayment.it.enabled=true 显式触发（防误跑）");
        }
    }

    @AfterAll
    static void stopContainer() {
        if (MYSQL != null) {
            MYSQL.stop();
        }
    }

    /** IT 最小切片：仅支付尝试链路 + MyBatis/Flyway/事务，不加载 Web/Nacos/MQ 自动装配。 */
    @Configuration
    @MapperScan({"com.cloudmart.payment.repository", "com.cloudmart.common.async.mapper"})
    @EnableTransactionManagement
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    static class ItSlice {
        @Bean
        PaymentAttemptService paymentAttemptService(PaymentAttemptMapper attemptMapper,
                                                    PaymentNotifyLogMapper notifyLogMapper,
                                                    OutboxService outboxService,
                                                    OrderFeignClient orderFeignClient,
                                                    ChannelRegistry channelRegistry,
                                                    PaymentChannelQueryService channelQueryService) {
            return new PaymentAttemptService(attemptMapper, notifyLogMapper, outboxService,
                    orderFeignClient, channelRegistry, channelQueryService,
                    "it-mock-secret", "https://it.example.com/return/", java.time.Clock.systemUTC());
        }

        @Bean
        OutboxService outboxService(com.cloudmart.common.async.mapper.OutboxEventMapper mapper) {
            return new OutboxService(mapper);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ChannelRegistry channelRegistry(org.springframework.core.env.Environment environment) {
            return new ChannelRegistry(List.of(), true, environment);
        }

        @Bean
        PaymentChannelCatalog paymentChannelCatalog() {
            return new PaymentChannelCatalog();
        }

        @Bean
        PaymentChannelQueryService paymentChannelQueryService(ChannelRegistry channelRegistry,
                                                               PaymentChannelCatalog catalog) {
            return new PaymentChannelQueryService(channelRegistry, catalog, "");
        }
    }

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private OrderFeignClient orderFeignClient;

    /** 每次运行随机高段订单号，外部共享 IT 库多运行/多机互踩也不冲突。 */
    private final long orderIdBase = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
    private static final Long USER_ID = 910_000_001L;
    private static final BigDecimal PAY_AMOUNT = new BigDecimal("88.00");

    @BeforeEach
    void resetStubs() {
        reset(orderFeignClient);
    }

    private void stubOrder(long orderId, long userId, String status) {
        when(orderFeignClient.getOrderInfo(orderId))
                .thenReturn(ApiResponse.ok(new OrderInternalInfoDTO(orderId, userId, status, PAY_AMOUNT, false)));
    }

    // ============================ AT18 核心并发 ============================

    /**
     * AT18：20 个线程对同一订单、同渠道同场景、各自独立 requestKey 并发创建。
     *
     * <p>真实 MySQL 语义：命名锁 + {@code SELECT ... FOR UPDATE} 让首笔之后的请求
     * 全部串行看到已存在的 PENDING 行并复用——业务不变量是<b>数据库中 PENDING 行恰好 1 笔、
     * 所有响应指向同一 attemptId</b>（而非依赖客户端去重）。</p>
     */
    @Test
    void concurrent_sameOrderDistinctKeys_convergesToSinglePendingAttempt() throws Exception {
        long orderId = orderIdBase + 1;
        stubOrder(orderId, USER_ID, "PENDING_PAYMENT");
        int concurrency = 20;

        List<PaymentAttemptService.CreateAttemptResult> results =
                runConcurrently(concurrency, orderId, () -> "key-" + UUID.randomUUID());

        assertThat(results).hasSize(concurrency);
        List<Long> attemptIds = results.stream()
                .map(r -> r.attempt().getId()).distinct().collect(Collectors.toList());
        assertThat(attemptIds).as("20 个并发响应必须收敛到同一笔支付尝试").hasSize(1);
        assertThat(results).allSatisfy(r -> {
            assertThat(r.replacedPrevious()).isFalse();
            assertThat(r.attempt().getStatus()).isEqualTo("PENDING");
            assertThat(r.attempt().getOrderId()).isEqualTo(orderId);
        });
        assertPendingAttemptCount(orderId, 1);
        assertThat(countBySql("SELECT COUNT(*) FROM payment_attempt WHERE order_id = " + orderId))
                .as("同订单历史总行数也必须为 1（未产生任何多余尝试）").isEqualTo(1L);
    }

    /**
     * 同一 requestKey 被 10 个线程并发重放：稳定幂等——所有响应同一 attemptId，
     * 数据库只有 1 行；这是弱网重试/双击的真实形态。
     */
    @Test
    void concurrent_sameRequestKey_replayStaysStable() throws Exception {
        long orderId = orderIdBase + 2;
        stubOrder(orderId, USER_ID, "PENDING_PAYMENT");
        String sharedKey = "replay-" + UUID.randomUUID();

        List<PaymentAttemptService.CreateAttemptResult> results =
                runConcurrently(10, orderId, () -> sharedKey);

        List<Long> attemptIds = results.stream()
                .map(r -> r.attempt().getId()).distinct().collect(Collectors.toList());
        assertThat(attemptIds).hasSize(1);
        assertPendingAttemptCount(orderId, 1);
        assertThat(countBySql("SELECT COUNT(*) FROM payment_attempt WHERE request_key = '" + sharedKey + "'"))
                .isEqualTo(1L);
    }

    /** 同键异参（同键换场景）必须 409 IDEMPOTENCY_KEY_REUSED，且不新增台账行。 */
    @Test
    void sameRequestKey_withDifferentScene_isRejected() {
        long orderId = orderIdBase + 3;
        stubOrder(orderId, USER_ID, "PENDING_PAYMENT");
        String key = "mismatch-" + UUID.randomUUID();

        PaymentAttemptService.CreateAttemptResult first = paymentAttemptService.createAttempt(
                USER_ID, orderId, "MOCK", PaymentScene.H5, key, null);
        assertThat(first.attempt().getScene()).isEqualTo("H5");

        assertThatThrownBy(() -> paymentAttemptService.createAttempt(
                USER_ID, orderId, "MOCK", PaymentScene.APP, key, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");

        assertPendingAttemptCount(orderId, 1);
    }

    /**
     * 命名锁被他人持有时，创建必须快速失败为 PAYMENT_ATTEMPT_BUSY（超时 5s 后），
     * 而不是无限等待或插出第二笔。用独立 JDBC 连接持锁，确定性复现竞争失败方路径。
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void orderLockHeldByAnother_createIsRejectedBusy() throws Exception {
        long orderId = orderIdBase + 4;
        stubOrder(orderId, USER_ID, "PENDING_PAYMENT");

        try (Connection lockConnection = dataSource.getConnection();
             Statement st = lockConnection.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT GET_LOCK(CONCAT('payment_attempt_order_', " + orderId + "), 0)")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("测试持锁必须成功").isEqualTo(1);
            }

            assertThatThrownBy(() -> paymentAttemptService.createAttempt(
                    USER_ID, orderId, "MOCK", PaymentScene.H5, "key-busy-" + UUID.randomUUID(), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo("PAYMENT_ATTEMPT_BUSY");
        }
        assertPendingAttemptCount(orderId, 0);
    }

    /** AT18 数据库层证明：两道唯一索引必须真实存在（约束可证明，而非仅代码注释声称）。 */
    @Test
    void databaseUniqueConstraints_arePresent() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME, NON_UNIQUE FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_attempt' "
                        + "AND INDEX_NAME IN ('uk_attempt_active_order', 'uk_attempt_request_key') "
                        + "GROUP BY INDEX_NAME, NON_UNIQUE");
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row ->
                assertThat(((Number) row.get("NON_UNIQUE")).intValue())
                        .as("索引 %s 必须唯一", row.get("INDEX_NAME")).isZero());
    }

    /** 归属防御：订单不属于当前用户 → PAYMENT_FORBIDDEN（并发之外的安全不变量，同链路顺带覆盖）。 */
    @Test
    void orderOwnedByOtherUser_isForbidden() {
        long orderId = orderIdBase + 5;
        stubOrder(orderId, 999_999_999L, "PENDING_PAYMENT");

        assertThatThrownBy(() -> paymentAttemptService.createAttempt(
                USER_ID, orderId, "MOCK", PaymentScene.H5, "key-forbidden-" + UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PAYMENT_FORBIDDEN");
    }

    /** 状态防御：订单非 PENDING_PAYMENT → PAYMENT_STATUS_ERROR。 */
    @Test
    void orderNotPayable_isRejected() {
        long orderId = orderIdBase + 6;
        stubOrder(orderId, USER_ID, "PAID");

        assertThatThrownBy(() -> paymentAttemptService.createAttempt(
                USER_ID, orderId, "MOCK", PaymentScene.H5, "key-status-" + UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PAYMENT_STATUS_ERROR");
    }

    // ============================ 辅助 ============================

    /**
     * 闸门并发执行：所有线程同时发起 createAttempt；异常与结果分开收集，
     * 避免 future.get 把并发失败吞成 ExecutionException 后难以归因。
     */
    private List<PaymentAttemptService.CreateAttemptResult> runConcurrently(
            int concurrency, long orderId, java.util.function.Supplier<String> requestKeySupplier)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        ConcurrentLinkedQueue<PaymentAttemptService.CreateAttemptResult> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>(concurrency);
        try {
            for (int i = 0; i < concurrency; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        results.add(paymentAttemptService.createAttempt(
                                USER_ID, orderId, "MOCK", PaymentScene.H5,
                                requestKeySupplier.get(), null));
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        done.countDown();
                    }
                }));
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).as("并发用例必须在 60s 内全部结束").isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(errors).as("并发创建不应抛出异常，实际：%s", errors).isEmpty();
        return new ArrayList<>(results);
    }

    private void assertPendingAttemptCount(long orderId, long expected) {
        Long pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_attempt WHERE order_id = ? AND status = 'PENDING'",
                Long.class, orderId);
        assertThat(pending).as("订单 %d 的 PENDING 支付尝试数", orderId).isEqualTo(expected);
    }

    private long countBySql(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
