package com.cloudmart.order.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.dto.CreateAfterSaleRequest;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import com.cloudmart.order.service.AfterSaleCaseService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRADE-01：售后并发占用（真实 MySQL 集成测试，T09）。
 *
 * <p>不变量：</p>
 * <ul>
 *   <li>同一订单行 20 个并发申请，总占用数量不超过购买数量（订单行锁串行化 + 数量占用）；</li>
 *   <li>整单申请与行申请并发，只有合法一方成立（有效占用覆盖整单与按行，无重叠）；</li>
 *   <li>同请求键重放（真实唯一键）返回原案件，不重复建案。</li>
 * </ul>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AfterSaleConcurrencyIntegrationTest.ItConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        })
@ActiveProfiles("it")
@DisplayName("TRADE-01 售后并发占用（真实 MySQL）")
class AfterSaleConcurrencyIntegrationTest {

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
    @MapperScan({"com.cloudmart.order.repository", "com.cloudmart.common.async.mapper"})
    @Import({com.cloudmart.order.service.impl.AfterSaleCaseServiceImpl.class,
            com.cloudmart.order.config.MyBatisPlusConfig.class})
    @EnableTransactionManagement
    static class ItConfig {

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        tools.jackson.databind.ObjectMapper objectMapper() {
            return new tools.jackson.databind.ObjectMapper();
        }

        /** TRADE-01：默认窗口 7 天（本 IT 不依赖属性绑定，直接给默认实例） */
        @Bean
        com.cloudmart.order.config.AfterSaleProperties afterSaleProperties() {
            return new com.cloudmart.order.config.AfterSaleProperties();
        }

        @Bean
        com.cloudmart.common.async.outbox.OutboxService outboxService(
                com.cloudmart.common.async.mapper.OutboxEventMapper mapper) {
            return new com.cloudmart.common.async.outbox.OutboxService(mapper);
        }
    }

    @Autowired
    private AfterSaleCaseService afterSaleCaseService;
    @Autowired
    private AfterSaleCaseMapper caseMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;

    private long seedOrderAndItem(long orderId, long itemId, long userId, int quantity, String payAmount) {
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setOrderNo("ITNO" + orderId);
        order.setTotalAmount(new BigDecimal(payAmount));
        order.setPayAmount(new BigDecimal(payAmount));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setStatus("PAID");
        order.setReceiverName("测试");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("测试地址");
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setOrderId(orderId);
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setProductName("测试商品");
        item.setPrice(new BigDecimal(payAmount).divide(BigDecimal.valueOf(quantity), 2,
                java.math.RoundingMode.DOWN));
        item.setQuantity(quantity);
        item.setPayAmount(new BigDecimal(payAmount));
        orderItemMapper.insert(item);
        return itemId;
    }

    @Test
    @DisplayName("T09：同一订单行 20 个并发申请，总占用不超过购买数量（恰好命中可申请上限）")
    void concurrentLineApplies_neverExceedPurchasedQuantity() throws Exception {
        long orderId = 90011L;
        long itemId = 50011L;
        seedOrderAndItem(orderId, itemId, 4201L, 3, "30.00");

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger quantityExceeded = new AtomicInteger();
        AtomicInteger otherReject = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    afterSaleCaseService.apply(4201L, new CreateAfterSaleRequest(
                            orderId, itemId, AfterSaleCase.TYPE_REFUND_ONLY,
                            "并发测试-" + index, null, 1), "it-race-key-" + index);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if ("AFTER_SALE_QUANTITY_EXCEEDED".equals(e.getCode())) {
                        quantityExceeded.incrementAndGet();
                    } else {
                        otherReject.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    otherReject.incrementAndGet();
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 购买 3 件：并发申请成功数恰为 3，其余全部按数量超限拒绝，占用总和 = 3
        assertThat(success.get()).isEqualTo(3);
        assertThat(quantityExceeded.get() + otherReject.get()).isEqualTo(threads - 3);
        List<AfterSaleCase> cases = caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, orderId));
        assertThat(cases).hasSize(3);
        assertThat(cases.stream().mapToInt(AfterSaleCase::getQuantity).sum()).isEqualTo(3);
    }

    @Test
    @DisplayName("T09：整单申请与行申请并发，只有合法一方成立（无重叠占用）")
    void concurrentWholeOrderAndLine_onlyOneWins() throws Exception {
        long orderId = 90012L;
        long itemId = 50012L;
        seedOrderAndItem(orderId, itemId, 4202L, 1, "10.00");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();

        Runnable whole = () -> {
            ready.countDown();
            try {
                go.await();
                afterSaleCaseService.apply(4202L, new CreateAfterSaleRequest(
                        orderId, null, AfterSaleCase.TYPE_REFUND_ONLY, "整单", null, 0), "it-whole-key-1");
                success.incrementAndGet();
            } catch (BusinessException e) {
                if ("AFTER_SALE_DUPLICATE".equals(e.getCode())) {
                    duplicate.incrementAndGet();
                }
            } catch (Exception ignored) {
                // 记入 duplicate 之外：断言会暴露
            }
        };
        Runnable line = () -> {
            ready.countDown();
            try {
                go.await();
                afterSaleCaseService.apply(4202L, new CreateAfterSaleRequest(
                        orderId, itemId, AfterSaleCase.TYPE_REFUND_ONLY, "按行", null, 1), "it-line-key-1");
                success.incrementAndGet();
            } catch (BusinessException e) {
                if ("AFTER_SALE_DUPLICATE".equals(e.getCode())) {
                    duplicate.incrementAndGet();
                }
            } catch (Exception ignored) {
                // 记入 duplicate 之外：断言会暴露
            }
        };
        Future<?> f1 = pool.submit(whole);
        Future<?> f2 = pool.submit(line);
        ready.await();
        go.countDown();
        f1.get(60, TimeUnit.SECONDS);
        f2.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(duplicate.get()).isEqualTo(1);
        List<AfterSaleCase> cases = caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, orderId));
        assertThat(cases).hasSize(1);
    }

    @Test
    @DisplayName("TRADE-01：同请求键重放（真实唯一键）返回原案件，不重复建案")
    void replaySameRequestKey_returnsOriginalCase() {
        long orderId = 90013L;
        long itemId = 50013L;
        seedOrderAndItem(orderId, itemId, 4203L, 1, "10.00");

        var request = new CreateAfterSaleRequest(
                orderId, itemId, AfterSaleCase.TYPE_REFUND_ONLY, "重放", null, 1);
        var first = afterSaleCaseService.apply(4203L, request, "it-replay-key-1");
        var second = afterSaleCaseService.apply(4203L, request, "it-replay-key-1");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(caseMapper.selectCount(new LambdaQueryWrapper<AfterSaleCase>()
                .eq(AfterSaleCase::getOrderId, orderId))).isEqualTo(1);
    }

    @Test
    @DisplayName("TRADE-01：行实付 10 元 3 件分批审批（1+1+1）累计恰好 10.00（真实 MySQL）")
    void tailAllocation_acrossApprovalsSumsExactly() {
        long orderId = 90014L;
        long itemId = 50014L;
        seedOrderAndItem(orderId, itemId, 4204L, 3, "10.00");

        // 三笔按件申请（每笔 1 件）
        Long case1 = applyCase(4204L, orderId, itemId, "tail-k1");
        Long case2 = applyCase(4204L, orderId, itemId, "tail-k2");
        Long case3 = applyCase(4204L, orderId, itemId, "tail-k3");

        // 依次审批：3.33 + 3.33 + 3.34 = 10.00（尾差归最后一个数量段）
        afterSaleCaseService.approve(1L, case1, new BigDecimal("3.33"));
        afterSaleCaseService.approve(1L, case2, new BigDecimal("3.33"));
        afterSaleCaseService.approve(1L, case3, new BigDecimal("3.34"));

        BigDecimal total = caseMapper.selectList(new LambdaQueryWrapper<AfterSaleCase>()
                        .eq(AfterSaleCase::getOrderId, orderId))
                .stream().map(AfterSaleCase::getRefundAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("10.00");
    }

    private Long applyCase(long userId, long orderId, long itemId, String key) {
        return afterSaleCaseService.apply(userId, new CreateAfterSaleRequest(
                orderId, itemId, AfterSaleCase.TYPE_REFUND_ONLY, "分批", null, 1), key).id();
    }
}
