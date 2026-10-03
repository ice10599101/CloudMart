package com.cloudmart.pet.wallet.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.entity.PetAssetGrant;
import com.cloudmart.pet.entity.PetPurchaseOrder;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetAssetGrantMapper;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService.PurchaseResult;
import com.cloudmart.pet.wallet.impl.PetPurchaseRecoveryService;
import com.cloudmart.pet.wallet.impl.PetRequestDedupServiceImpl;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P02/QA35：购买幂等完成与资产交付不可分离（真实 MySQL 集成测试）。
 *
 * <p>覆盖的崩溃边界：</p>
 * <ul>
 *   <li>claim 提交后、业务事务前崩溃 → 租约到期接管 → 同键重试重建，无残留悬挂；</li>
 *   <li>业务已提交而幂等键残留 PROCESSING（执行者卡死的历史崩溃形态）→
 *       恢复扫描器按本地业务事实（订单/流水/资产）收敛终态，<b>不重扣不重发</b>；</li>
 *   <li>业务事务原子回滚后无任何事实 → 恢复置 FAILED 释放同键重试。</li>
 * </ul>
 *
 * <p>终态不变量：一次购买意图恰好一订单、一扣款、一资产授予；同键重放返回原结果。</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PetPurchaseRecoveryIntegrationTest.PurchaseItConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "pet.request-dedup.lease-seconds=300"
        })
@ActiveProfiles("it")
@DisplayName("P02/QA35 购买幂等崩溃恢复（真实 MySQL）")
class PetPurchaseRecoveryIntegrationTest {

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
    @Import({PetWalletServiceImpl.class, PetRequestDedupServiceImpl.class,
            PetPurchaseApplicationService.class, PetPurchaseRecoveryService.class,
            com.cloudmart.pet.config.MyBatisPlusConfig.class, com.cloudmart.pet.config.PetClock.class})
    @EnableTransactionManagement
    static class PurchaseItConfig {

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        /** 采购归属校验依赖 PetService；IT 只验钱包事实，归属恒真即可 */
        @Bean
        com.cloudmart.pet.service.PetService petService() {
            return org.mockito.Mockito.mock(com.cloudmart.pet.service.PetService.class);
        }

        /** 测试目录：服务端权威价格，FOOD 可重复购买 */
        @Bean
        PetPurchaseCatalog testCatalog() {
            return new PetPurchaseCatalog() {
                @Override
                public CatalogEntry load(Long userId, Long petId, String itemType, String itemCode,
                                         String expectedConfigVersion) {
                    return new CatalogEntry(itemType, itemCode, 10L, "v1", "测试物品", "res-" + itemCode);
                }

                @Override
                public boolean isUniquePerPet(String itemType) {
                    return false;
                }

                @Override
                public boolean isOwnedByPet(Long petId, String itemType, String itemCode) {
                    return false;
                }
            };
        }

        /** 测试交付器：业务库存无副作用，仅返回槽位（asset_grant 事实仍由应用服务记录） */
        @Bean
        PetPurchaseCatalog.PetAssetDeliverer testDeliverer() {
            return new PetPurchaseCatalog.PetAssetDeliverer() {
                @Override
                public boolean supports(String itemType) {
                    return true;
                }

                @Override
                public java.util.List<String> deliver(DeliveryContext context) {
                    return java.util.List.of(context.itemCode());
                }
            };
        }
    }

    @Autowired
    private PetPurchaseApplicationService purchaseService;
    @Autowired
    private PetPurchaseRecoveryService recoveryService;
    @Autowired
    private PetRequestDedupService dedupService;
    @Autowired
    private PetRequestDedupMapper dedupMapper;
    @Autowired
    private PetPurchaseOrderMapper orderMapper;
    @Autowired
    private PetWalletTransactionMapper transactionMapper;
    @Autowired
    private PetAssetGrantMapper assetGrantMapper;

    private static final String REQUEST_KEY = "it-purchase-key-0001";

    private PurchaseResult purchase(long userId, String requestKey) {
        return purchaseService.purchase(userId, 5L, "FOOD", "cake", requestKey, "v1");
    }

    private long countOrders(long userId) {
        return orderMapper.selectCount(new LambdaQueryWrapper<PetPurchaseOrder>()
                .eq(PetPurchaseOrder::getUserId, userId));
    }

    private long countDebits(long userId) {
        return transactionMapper.selectCount(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getUserId, userId)
                .eq(PetWalletTransaction::getDirection, "SPEND"));
    }

    private long countGrants(long userId) {
        return assetGrantMapper.selectCount(new LambdaQueryWrapper<PetAssetGrant>()
                .eq(PetAssetGrant::getUserId, userId));
    }

    private PetRequestDedup dedupRow(long userId) {
        return dedupMapper.selectOne(new LambdaQueryWrapper<PetRequestDedup>()
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, "PURCHASE")
                .eq(PetRequestDedup::getRequestKey, REQUEST_KEY));
    }

    private void expireLeaseAsIfCrashed(long userId) {
        // 将 dedup 行改写为"执行者已丢失"的崩溃残留形态（PROCESSING + 租约过期）
        dedupMapper.update(null, new LambdaUpdateWrapper<PetRequestDedup>()
                .set(PetRequestDedup::getStatus, "PROCESSING")
                .set(PetRequestDedup::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10))
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, "PURCHASE")
                .eq(PetRequestDedup::getRequestKey, REQUEST_KEY));
    }

    @Test
    @DisplayName("正常购买与同键重放：一订单/一扣款/一资产授予，重放返回原结果")
    void purchaseAndReplay_oneFactEach() {
        long userId = 3001;

        PurchaseResult first = purchase(userId, REQUEST_KEY);
        assertThat(first.duplicate()).isFalse();
        assertThat(first.orderId()).isNotBlank();
        assertThat(first.errorCode()).isNull();

        assertThat(countOrders(userId)).isEqualTo(1);
        assertThat(countDebits(userId)).isEqualTo(1);
        assertThat(countGrants(userId)).isEqualTo(1);
        PetRequestDedup row = dedupRow(userId);
        assertThat(row.getStatus()).isEqualTo("COMPLETED");
        assertThat(row.getResponseJson()).contains(first.orderId());

        PurchaseResult replay = purchase(userId, REQUEST_KEY);
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(countOrders(userId)).isEqualTo(1);
        assertThat(countDebits(userId)).isEqualTo(1);
        assertThat(countGrants(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("QA35 业务提交后执行者丢失：恢复扫描器按事实收敛，不重扣不重发")
    void crashedAfterCommit_recoveredByFacts() {
        long userId = 3002;
        PurchaseResult first = purchase(userId, REQUEST_KEY);
        assertThat(countDebits(userId)).isEqualTo(1);

        // 模拟历史崩溃形态：业务事实已提交，但幂等键残留 PROCESSING（租约已过期）
        expireLeaseAsIfCrashed(userId);

        int recovered = recoveryService.recoverExpiredLeases();
        assertThat(recovered).isEqualTo(1);

        PetRequestDedup row = dedupRow(userId);
        assertThat(row.getStatus()).isEqualTo("COMPLETED");
        assertThat(row.getBizOrderId()).isEqualTo(Long.valueOf(first.orderId()));
        assertThat(row.getResponseJson()).contains(first.orderId());

        // 同键查询拿回原结果；业务事实不增加
        PurchaseResult replay = purchase(userId, REQUEST_KEY);
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(countOrders(userId)).isEqualTo(1);
        assertThat(countDebits(userId)).isEqualTo(1);
        assertThat(countGrants(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("QA35 claim 后业务事务前崩溃：租约到期接管后同键重试，无悬挂残留")
    void crashedBeforeCommit_retryAfterTakeover() {
        long userId = 3003;
        // 模拟崩溃：仅占键（REQUIRES_NEW 已提交），业务事务从未开始
        dedupService.claim(userId, "PURCHASE", REQUEST_KEY,
                dedupService.canonicalHash(userId, "5", "FOOD", "cake", "v1"));
        expireLeaseAsIfCrashed(userId);

        // 同键重试：claim 发现租约到期自动接管，按 NEW 重新执行
        PurchaseResult result = purchase(userId, REQUEST_KEY);
        assertThat(result.duplicate()).isFalse();
        assertThat(countOrders(userId)).isEqualTo(1);
        assertThat(countDebits(userId)).isEqualTo(1);
        assertThat(countGrants(userId)).isEqualTo(1);
        assertThat(dedupRow(userId).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("QA35 业务回滚无事实：恢复置 FAILED，同键重试安全重建")
    void rolledBackNoFact_recoveryMarksRetryable() {
        long userId = 3004;
        dedupService.claim(userId, "PURCHASE", REQUEST_KEY,
                dedupService.canonicalHash(userId, "5", "FOOD", "cake", "v1"));
        expireLeaseAsIfCrashed(userId);

        assertThat(recoveryService.recoverExpiredLeases()).isEqualTo(1);
        assertThat(dedupRow(userId).getStatus()).isEqualTo("FAILED");
        assertThat(countOrders(userId)).isZero();
        assertThat(countDebits(userId)).isZero();

        // 同键重试：FAILED→PROCESSING CAS 后重新执行成功
        PurchaseResult result = purchase(userId, REQUEST_KEY);
        assertThat(result.duplicate()).isFalse();
        assertThat(countOrders(userId)).isEqualTo(1);
        assertThat(countDebits(userId)).isEqualTo(1);
        assertThat(countGrants(userId)).isEqualTo(1);
    }
}
