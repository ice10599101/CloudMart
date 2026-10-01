package com.cloudmart.inventory.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.inventory.config.RedisConfig;
import com.cloudmart.inventory.converter.InventoryConverterImpl;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.ReleaseRequest;
import com.cloudmart.inventory.entity.Inventory;
import com.cloudmart.inventory.entity.InventoryReservation;
import com.cloudmart.inventory.repository.InventoryLogMapper;
import com.cloudmart.inventory.repository.InventoryMapper;
import com.cloudmart.inventory.repository.InventoryReservationMapper;
import com.cloudmart.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T04/QA12：库存预占真实 MySQL + Redis 集成测试。
 *
 * <p>覆盖验收矩阵：</p>
 * <ul>
 *   <li>库存 10、100 并发购买 → 无超卖、无负数、事实与汇总相等；</li>
 *   <li>A 订单重复释放 → 幂等成功且不影响 B 订单预占；</li>
 *   <li>同事实 (orderId, skuId) 同量预占重放成功、异量冲突；</li>
 *   <li>释放/确认按台账 CAS 迁移，确认数量以台账为准。</li>
 * </ul>
 *
 * <p>Redis（Lua 预扣 + Redisson 锁）与 MySQL（条件更新 + 行锁 + 预占台账）均为真实容器；
 * CI（ubuntu runner 自带 Docker）必跑，本地无 Docker 跳过。</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = InventoryReservationIntegrationTest.InventoryItConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        })
@ActiveProfiles("it")
@DisplayName("T04/QA12 库存预占事实（真实 MySQL+Redis）")
class InventoryReservationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:9.4.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Configuration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            DataRedisAutoConfiguration.class, RedissonAutoConfigurationV4.class})
    @MapperScan("com.cloudmart.inventory.repository")
    @Import({RedisConfig.class, InventoryConverterImpl.class, InventoryServiceImpl.class})
    @EnableTransactionManagement
    static class InventoryItConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private InventoryServiceImpl inventoryService;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private InventoryReservationMapper reservationMapper;
    @Autowired
    private InventoryLogMapper inventoryLogMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final AtomicLong SKU_SEQ = new AtomicLong(50000);
    private static final AtomicLong ORDER_SEQ = new AtomicLong(900000);

    private long newSku(int available) {
        long skuId = SKU_SEQ.incrementAndGet();
        inventoryService.initStock(skuId, 1L, available);
        return skuId;
    }

    private long newOrderId() {
        return ORDER_SEQ.incrementAndGet();
    }

    private Inventory inventoryOf(long skuId) {
        return inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, skuId));
    }

    @AfterEach
    void cleanCache() {
        // 每用例独立 SKU，无需清库；仅清缓存键避免跨用例误读
        var keys = redisTemplate.keys("inventory:product:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("QA12：库存 10、100 并发购买 → 恰好 10 单成功，无超卖无负数，事实与汇总相等")
    void hundredConcurrentBuysOnTenStock_neverOversell() throws Exception {
        long skuId = newSku(10);
        int threads = 100;
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                long orderId = newOrderId();
                futures.add(pool.submit((Callable<Boolean>) () -> {
                    start.await();
                    return inventoryService.deductStock(new DeductRequest(skuId, 1, orderId));
                }));
            }
            start.countDown();
            int success = 0;
            for (Future<Boolean> future : futures) {
                if (Boolean.TRUE.equals(future.get(120, TimeUnit.SECONDS))) {
                    success++;
                }
            }
            assertThat(success).as("库存 10 只允许 10 个订单预占成功").isEqualTo(10);
        }

        Inventory inventory = inventoryOf(skuId);
        assertThat(inventory.getAvailable()).isZero();
        assertThat(inventory.getReserved()).isEqualTo(10);

        // 事实与汇总相等：台账 RESERVED 行数量合计 = 库存行 reserved
        Integer ledgerReserved = reservationMapper.selectList(new LambdaQueryWrapper<InventoryReservation>()
                        .eq(InventoryReservation::getSkuId, skuId)
                        .eq(InventoryReservation::getStatus, "RESERVED"))
                .stream().mapToInt(InventoryReservation::getQuantity).sum();
        assertThat(ledgerReserved).isEqualTo(inventory.getReserved());

        // 每条成功预占都有 DEDUCT 流水，无多余流水
        long deductLogs = inventoryLogMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.inventory.entity.InventoryLog>()
                .eq(com.cloudmart.inventory.entity.InventoryLog::getSkuId, skuId)
                .eq(com.cloudmart.inventory.entity.InventoryLog::getType, "DEDUCT"));
        assertThat(deductLogs).isEqualTo(10);
    }

    @Test
    @DisplayName("QA12：A 订单重复释放幂等成功，不影响 B 订单预占")
    void duplicateRelease_isolatedBetweenOrders() {
        long skuId = newSku(10);
        long orderA = newOrderId();
        long orderB = newOrderId();

        assertThat(inventoryService.deductStock(new DeductRequest(skuId, 3, orderA))).isTrue();
        assertThat(inventoryService.deductStock(new DeductRequest(skuId, 4, orderB))).isTrue();
        assertThat(inventoryOf(skuId).getAvailable()).isEqualTo(3);

        inventoryService.releaseStock(new ReleaseRequest(skuId, 3, orderA));
        inventoryService.releaseStock(new ReleaseRequest(skuId, 3, orderA));

        Inventory inventory = inventoryOf(skuId);
        assertThat(inventory.getAvailable()).as("重复释放不得多次回补可售库存").isEqualTo(6);
        assertThat(inventory.getReserved()).as("重复释放不得多次扣减预占").isEqualTo(4);
        // B 订单预占事实不受影响
        assertThat(reservationMapper.findByOrderAndSku(orderB, skuId).getStatus()).isEqualTo("RESERVED");
        assertThat(reservationMapper.findByOrderAndSku(orderA, skuId).getStatus()).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("T04：同事实同量预占重放成功（不重复扣减）；同事实异量冲突")
    void deduct_replayAndConflict() {
        long skuId = newSku(10);
        long orderId = newOrderId();

        assertThat(inventoryService.deductStock(new DeductRequest(skuId, 3, orderId))).isTrue();
        assertThat(inventoryService.deductStock(new DeductRequest(skuId, 3, orderId))).isTrue();
        assertThat(inventoryOf(skuId).getReserved()).as("同事实重放不得重复预占").isEqualTo(3);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> inventoryService.deductStock(new DeductRequest(skuId, 5, orderId)))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class);
        assertThat(inventoryOf(skuId).getReserved()).isEqualTo(3);
    }

    @Test
    @DisplayName("T04/LC04：零订单预占被拒绝；无台账释放被拒绝并核查")
    void zeroOrderAndMissingLedger_rejected() {
        long skuId = newSku(10);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> inventoryService.deductStock(new DeductRequest(skuId, 3, 0L)))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_ORDER_REQUIRED");

        long orderId = newOrderId();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> inventoryService.releaseStock(new ReleaseRequest(skuId, 3, orderId)))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_RESERVATION_MISSING");
        assertThat(inventoryOf(skuId).getAvailable()).isEqualTo(10);
    }

    @Test
    @DisplayName("T04：确认以台账数量为准，状态机一次性迁移；确认后不可再释放")
    void confirmLedgerDriven_onceOnly() {
        long skuId = newSku(10);
        long orderId = newOrderId();
        assertThat(inventoryService.deductStock(new DeductRequest(skuId, 3, orderId))).isTrue();

        inventoryService.confirmDeduct(skuId, 2, orderId);

        Inventory inventory = inventoryOf(skuId);
        assertThat(inventory.getAvailable()).isEqualTo(7);
        assertThat(inventory.getReserved()).isZero();
        assertThat(reservationMapper.findByOrderAndSku(orderId, skuId).getStatus()).isEqualTo("CONFIRMED");

        // 已确认销售：不得按释放回补可售库存（退货应走 RETURN_INBOUND）
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> inventoryService.releaseStock(new ReleaseRequest(skuId, 3, orderId)))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_RELEASE_CONFLICT");
        assertThat(inventoryOf(skuId).getAvailable()).isEqualTo(7);
    }
}
