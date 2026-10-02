package com.cloudmart.seckill.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.cloudmart.seckill.config.MyBatisPlusConfig;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import com.cloudmart.seckill.repository.SeckillRequestMapper;
import com.cloudmart.seckill.service.SeckillRequestService;
import com.cloudmart.seckill.service.impl.SeckillRequestServiceImpl;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T09/QA17：秒杀请求事实状态机（真实 MySQL）。
 *
 * <p>覆盖：资格占用（库存原子预减 + 唯一键）、终态失败释放与重发起（换新
 * requestId）、购买限额并发裁决（100 线程同用户仅一席位）、库存售罄裁决
 * （多用户恰好售罄数成功）、成功 CAS 结算。Redis 预筛/执行编排不在此层。</p>
 *
 * <p>CI（ubuntu runner 自带 Docker）必跑，本地无 Docker 跳过。</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SeckillRequestStateMachineIntegrationTest.SeckillItConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        })
@DisplayName("T09/QA17 秒杀请求事实状态机（真实 MySQL）")
class SeckillRequestStateMachineIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:9.4.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Configuration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    @MapperScan({"com.cloudmart.seckill.repository", "com.cloudmart.common.async.mapper"})
    @Import({MyBatisPlusConfig.class, SeckillRequestServiceImpl.class})
    @EnableTransactionManagement
    static class SeckillItConfig {
    }

    @Autowired
    private SeckillRequestService requestService;
    @Autowired
    private SeckillRequestMapper requestMapper;
    @Autowired
    private SeckillProductMapper productMapper;

    private static final BigDecimal PRICE = new BigDecimal("199.00");

    private long newProduct(int stock) {
        SeckillProduct product = new SeckillProduct();
        product.setActivityId(99001L);
        product.setSkuId(88001L);
        product.setSeckillPrice(PRICE);
        product.setOriginalPrice(new BigDecimal("298.00"));
        product.setTotalStock(stock);
        product.setAvailableStock(stock);
        product.setPerUserLimit(1);
        product.setStatus("ON_SHELF");
        productMapper.insert(product);
        return product.getId();
    }

    private int availableOf(long productId) {
        return productMapper.selectById(productId).getAvailableStock();
    }

    @Test
    @DisplayName("T09：占用→终态失败释放→重发起换新 requestId（远程 500 路径的真实 MySQL 复现）")
    void holdSettleFail_reinitiateWithNewRequestId() {
        long productId = newProduct(5);

        SeckillRequest held = requestService.holdSeat(1001L, 99001L, productId, 88001L, PRICE, 1);
        assertThat(held.getStatus()).isEqualTo("PENDING");
        assertThat(availableOf(productId)).isEqualTo(4);

        // 对账收口：终态失败并释放（恢复任务路径）
        assertThat(requestService.settleFailed(held.getRequestId(), "排队超时，请重新发起")).isTrue();
        assertThat(requestService.releaseSeat(productId)).isTrue();
        assertThat(availableOf(productId)).isEqualTo(5);

        // 终态失败重发起：复用原行换新 requestId（execute 500 的分支）
        SeckillRequest reheld = requestService.holdSeat(1001L, 99001L, productId, 88001L, PRICE, 1);

        assertThat(reheld.getId()).isEqualTo(held.getId());
        assertThat(reheld.getRequestId()).isNotEqualTo(held.getRequestId());
        assertThat(reheld.getStatus()).isEqualTo("PENDING");
        assertThat(reheld.getFailReason()).isNull();
        assertThat(reheld.getSendAttempts()).isZero();
        assertThat(availableOf(productId)).isEqualTo(4);
    }

    @Test
    @DisplayName("QA17：100 线程同 (activity,product,user) → 恰好一席位，库存只减 1")
    void hundredConcurrentSameUser_exactlyOneSeat() throws Exception {
        long productId = newProduct(50);
        int threads = 100;
        List<SeckillRequest> winners = new CopyOnWriteArrayList<>();
        Set<String> failureCodes = java.util.concurrent.ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Boolean>) () -> {
                    start.await();
                    try {
                        synchronized (winners) {
                            winners.add(requestService.holdSeat(2001L, 99001L, productId, 88001L, PRICE, 1));
                        }
                        return true;
                    } catch (SeckillRequestService.SeatExistsException e) {
                        failureCodes.add("SEAT_EXISTS");
                        return false;
                    } catch (RuntimeException e) {
                        failureCodes.add(e.getClass().getSimpleName() + ":" + e.getMessage());
                        return false;
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        }

        assertThat(winners).as("购买限额事实：同用户只允许一个席位").hasSize(1);
        assertThat(failureCodes).as("其余 99 次全部以席位已存在拒绝").hasSize(99).containsOnly("SEAT_EXISTS");
        assertThat(availableOf(productId)).isEqualTo(49);
    }

    @Test
    @DisplayName("QA17：库存 2、3 用户并发 → 恰好 2 成功，第 3 个 DB 口径售罄")
    void threeConcurrentUsersOnTwoStock_exactlyTwoSeats() throws Exception {
        long productId = newProduct(2);
        List<Long> userIds = List.of(3001L, 3002L, 3003L);
        List<SeckillRequest> winners = new CopyOnWriteArrayList<>();
        Set<String> failures = java.util.concurrent.ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (Long userId : userIds) {
                futures.add(pool.submit((Callable<Boolean>) () -> {
                    start.await();
                    try {
                        synchronized (winners) {
                            winners.add(requestService.holdSeat(userId, 99001L, productId, 88001L, PRICE, 1));
                        }
                        return true;
                    } catch (SeckillRequestService.SeatSoldOutException e) {
                        failures.add("SOLD_OUT");
                        return false;
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        }

        assertThat(winners).as("库存 2 恰好成交 2").hasSize(2);
        assertThat(failures).containsExactly("SOLD_OUT");
        assertThat(availableOf(productId)).isZero();
    }

    @Test
    @DisplayName("T09：成功 CAS 结算关联订单；重复结算不二次生效")
    void settleSuccess_casAndIdempotent() {
        long productId = newProduct(5);
        SeckillRequest held = requestService.holdSeat(4001L, 99001L, productId, 88001L, PRICE, 1);

        assertThat(requestService.settleSuccess(held.getRequestId(), 777L)).isTrue();
        assertThat(requestService.settleSuccess(held.getRequestId(), 888L)).isFalse();

        SeckillRequest settled = requestService.findByRequestId(held.getRequestId());
        assertThat(settled.getStatus()).isEqualTo("SUCCESS");
        assertThat(settled.getOrderId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("T09：恢复扫描只捞到 PENDING 到期行，终态行不被重放")
    void findPendingDue_onlyPendingRows() {
        long productId = newProduct(5);
        SeckillRequest pending = requestService.holdSeat(5001L, 99001L, productId, 88001L, PRICE, 1);
        SeckillRequest done = requestService.holdSeat(5002L, 99001L, productId, 88001L, PRICE, 1);
        requestService.settleSuccess(done.getRequestId(), 900L);

        List<SeckillRequest> due = requestService.findPendingDue(java.time.LocalDateTime.now().plusMinutes(1), 100);

        assertThat(due).extracting(SeckillRequest::getRequestId).containsExactly(pending.getRequestId());
    }
}
