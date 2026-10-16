package com.cloudmart.pet.wallet.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetAssetGrant;
import com.cloudmart.pet.entity.PetPurchaseOrder;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetAssetGrantMapper;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService.PurchaseResult;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PET-01：显式 petId 购买归属校验（真实 MySQL 事务集成测试，T23/T24）。
 *
 * <p>不变量：</p>
 * <ul>
 *   <li>向他人宠物购买 → {@code PET_NOT_OWNER}，任何订单/扣款/资产授予事实零产生，
 *       拒绝终态落库且同键重放返回同一拒绝；</li>
 *   <li>本人宠物购买 → 一订单/一扣款/一资产授予，事实携带正确 petId；</li>
 *   <li>同键切宠（异参）→ {@code PET_IDEMPOTENCY_CONFLICT}，原意图不改投新宠。</li>
 * </ul>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PetPurchaseOwnershipIntegrationTest.OwnershipItConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "pet.request-dedup.lease-seconds=300"
        })
@ActiveProfiles("it")
@DisplayName("PET-01 购买归属校验（真实 MySQL）")
class PetPurchaseOwnershipIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:9.4.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    /** 归属规则：501/502 属于用户 4001；900 属于用户 1（供越权用例使用） */
    private static final Map<Long, Long> PET_OWNER = Map.of(
            501L, 4001L,
            502L, 4001L,
            900L, 1L);

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
            PetPurchaseApplicationService.class,
            com.cloudmart.pet.config.MyBatisPlusConfig.class, com.cloudmart.pet.config.PetClock.class})
    @EnableTransactionManagement
    static class OwnershipItConfig {

        @Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }

        @Bean
        com.cloudmart.pet.config.PetProperties petProperties() {
            return new com.cloudmart.pet.config.PetProperties();
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        /** 归属规则桩：与真实 PetService.requireOwnedPet(userId, petId) 同语义（不存在/非本人统一 PET_NOT_OWNER） */
        @Bean
        com.cloudmart.pet.service.PetService petService() {
            com.cloudmart.pet.service.PetService mock =
                    org.mockito.Mockito.mock(com.cloudmart.pet.service.PetService.class);
            org.mockito.Mockito.lenient().when(mock.requireOwnedPet(
                            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                    .thenAnswer(inv -> {
                        Long userId = inv.getArgument(0);
                        Long petId = inv.getArgument(1);
                        Long owner = PET_OWNER.get(petId);
                        if (owner == null || !owner.equals(userId)) {
                            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "宠物不存在或无权访问");
                        }
                        com.cloudmart.pet.entity.Pet pet = new com.cloudmart.pet.entity.Pet();
                        pet.setId(petId);
                        pet.setUserId(owner);
                        return pet;
                    });
            return mock;
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
    private PetWalletService walletService;
    @Autowired
    private PetWalletTransactionMapper transactionMapper;
    @Autowired
    private PetPurchaseOrderMapper orderMapper;
    @Autowired
    private PetAssetGrantMapper assetGrantMapper;
    @Autowired
    private PetRequestDedupMapper dedupMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private void seedWallet(long userId, long amount) {
        transactionTemplate.executeWithoutResult(status -> walletService.credit(
                new PetWalletService.PetWalletCommand(userId, 501L, "EARN",
                        "SEED", "seed:" + userId, amount, "pw_seed_" + userId, "hash_seed_" + userId,
                        null, "v1", null)));
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

    private PetRequestDedup dedupRow(long userId, String requestKey) {
        return dedupMapper.selectOne(new LambdaQueryWrapper<PetRequestDedup>()
                .eq(PetRequestDedup::getUserId, userId)
                .eq(PetRequestDedup::getEndpointKey, "PURCHASE")
                .eq(PetRequestDedup::getRequestKey, requestKey));
    }

    @Test
    @DisplayName("T23：向他人 petId 购买 → PET_NOT_OWNER，双方钱包/订单/资产零写入，拒绝终态可重放")
    void purchaseOthersPet_rejectedWithZeroFacts() {
        long attacker = 4101L;
        String requestKey = "it-own-key-attacker-01";
        seedWallet(attacker, 100);

        assertThatThrownBy(() -> purchaseService.purchase(attacker, 900L, "FOOD", "cake", requestKey, "v1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_NOT_OWNER));

        // 攻击者：无订单、无扣款、无资产授予；余额保持
        assertThat(countOrders(attacker)).isZero();
        assertThat(countDebits(attacker)).isZero();
        assertThat(countGrants(attacker)).isZero();
        assertThat(walletService.getOrCreateAccount(attacker).getBalance()).isEqualTo(100);
        // 宠物主人（用户 1）侧同样零写入，不存在"资产被写到他人宠物"的污染
        assertThat(countOrders(1L)).isZero();
        assertThat(countGrants(1L)).isZero();

        // 拒绝终态已落库（COMPLETED + errorCode），同键重放返回同一拒绝，不重新执行
        PetRequestDedup row = dedupRow(attacker, requestKey);
        assertThat(row).isNotNull();
        assertThat(row.getStatus()).isEqualTo("COMPLETED");
        assertThat(row.getResponseJson()).contains(PetErrorCodes.PET_NOT_OWNER);
        assertThatThrownBy(() -> purchaseService.purchase(attacker, 900L, "FOOD", "cake", requestKey, "v1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_NOT_OWNER));
        assertThat(countDebits(attacker)).isZero();
    }

    @Test
    @DisplayName("本人宠物购买：一订单/一扣款/一资产授予，事实携带正确 petId")
    void purchaseOwnPet_oneFactEach() {
        long userId = 4102L;
        seedWallet(userId, 100);

        PurchaseResult result = purchaseService.purchase(userId, 501L, "FOOD", "cake",
                "it-own-key-owner-01", "v1");

        assertThat(result.duplicate()).isFalse();
        assertThat(result.petId()).isEqualTo(501L);
        assertThat(countOrders(userId)).isEqualTo(1);
        assertThat(countDebits(userId)).isEqualTo(1);
        assertThat(countGrants(userId)).isEqualTo(1);

        PetAssetGrant grant = assetGrantMapper.selectOne(new LambdaQueryWrapper<PetAssetGrant>()
                .eq(PetAssetGrant::getUserId, userId));
        assertThat(grant.getPetId()).isEqualTo(501L);

        // 同用户另一只宠物按各自归属独立购买
        PurchaseResult second = purchaseService.purchase(userId, 502L, "FOOD", "cake",
                "it-own-key-owner-02", "v1");
        assertThat(second.petId()).isEqualTo(502L);
        assertThat(countOrders(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("同键切宠（异参）→ PET_IDEMPOTENCY_CONFLICT，原意图不改投新宠")
    void sameKeyDifferentPet_conflict() {
        long userId = 4103L;
        String requestKey = "it-own-key-switch-pet-01";
        seedWallet(userId, 100);

        PurchaseResult first = purchaseService.purchase(userId, 501L, "FOOD", "cake", requestKey, "v1");
        assertThat(first.petId()).isEqualTo(501L);

        assertThatThrownBy(() -> purchaseService.purchase(userId, 502L, "FOOD", "cake", requestKey, "v1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(PetErrorCodes.PET_IDEMPOTENCY_CONFLICT));
        // 只有原宠物的那一笔事实
        assertThat(countOrders(userId)).isEqualTo(1);
        assertThat(countDebits(userId)).isEqualTo(1);
    }
}
