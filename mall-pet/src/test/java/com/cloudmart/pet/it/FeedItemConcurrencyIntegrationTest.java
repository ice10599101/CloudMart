package com.cloudmart.pet.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetFeedEntitlement;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.feign.CouponFeignClient;
import com.cloudmart.pet.feign.WishUserProfileFeignClient;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetFeedEntitlementMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetHomeService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetInteractionService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.impl.PetActivityMutex;
import com.cloudmart.pet.service.impl.PetCompanionFeatureService;
import com.cloudmart.pet.service.impl.PetCooperationService;
import com.cloudmart.pet.service.impl.PetFriendFeedService;
import com.cloudmart.pet.service.impl.PetInteractionServiceImpl;
import com.cloudmart.pet.service.impl.PetItemCatalog;
import com.cloudmart.pet.service.impl.PetLinkedProductServiceImpl;
import com.cloudmart.pet.service.impl.PetOutboxService;
import com.cloudmart.pet.service.impl.PetQuotaService;
import com.cloudmart.pet.service.impl.PetRankingCache;
import com.cloudmart.pet.service.impl.PetStateService;
import com.cloudmart.pet.service.support.UserPetTimezoneResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AT04（R02/R25）物品喂食操作幂等与双倍喂食权益并发安全真实 MySQL 9 集成测试。
 *
 * <p>审计验收（实施方案 §6/R02）：双端同喂食键并发只结算一次；切宠后重放不串宠；
 * 权益占用失败者不得获得双倍；权益按用户隔离。这些不变量依赖真实 InnoDB 行锁
 * （{@code SELECT ... FOR UPDATE}）、唯一键（{@code uk_feed_operation}）与
 * CAS 条件更新语义，H2/Mockito 无证明力。</p>
 *
 * <p>被测真实链路：{@link PetInteractionServiceImpl#feedItem}
 * （主宠行 FOR UPDATE → 背包条件扣减 →
 * {@link PetLinkedProductServiceImpl#consumeOneForFeed} CAS 占用权益 → 数值 LEAST 封顶落库
 * → 活动留痕）。旁路协作者（任务/成就/亲密度/合作/动态扇出/MQ/排行榜缓存/Feign）
 * 以 MockitoBean 替代——它们不承担 AT04 任何不变量；不加载 Web/Nacos/Sentinel/Redis。</p>
 *
 * <h3>基础设施双模式</h3>
 * <ul>
 *   <li>默认：Testcontainers 自起 mysql:9.4.0（CI runner 自带 Docker）；</li>
 *   <li>外部模式：{@code PET_IT_MYSQL_HOST} 直连授权 IT 环境 mysql-it(8307)，
 *       schema {@code mall_pet_it} 与业务库隔离，须 {@code -Dpet.it.enabled=true}。
 *       每运行随机高段用户/主键，@AfterEach 按 ID 精确清理，不做 TRUNCATE。</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest(
        classes = FeedItemConcurrencyIntegrationTest.FeedItSlice.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.validate-on-migrate=false",
                // 并发用例：2 工作线程 + 事务持有连接，连接池需放宽
                "spring.datasource.hikari.maximum-pool-size=30"
        })
@ActiveProfiles("it")
@DisplayName("AT04 物品喂食操作幂等/双倍权益并发（真实 MySQL）")
class FeedItemConcurrencyIntegrationTest {

    private static final boolean USE_EXTERNAL_INFRA = System.getenv("PET_IT_MYSQL_HOST") != null;

    static final MySQLContainer<?> MYSQL = USE_EXTERNAL_INFRA ? null
            : new MySQLContainer<>("mysql:9.4.0").withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_INFRA) {
            String host = System.getenv("PET_IT_MYSQL_HOST");
            String port = System.getenv().getOrDefault("PET_IT_MYSQL_PORT", "8307");
            registry.add("spring.datasource.url",
                    () -> "jdbc:mysql://" + host + ":" + port
                            + "/mall_pet_it?useUnicode=true&characterEncoding=utf8&useSSL=false"
                            + "&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true");
            registry.add("spring.datasource.username", () -> "root");
            registry.add("spring.datasource.password", () -> "123456");
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
        String enabled = System.getProperty("pet.it.enabled", System.getenv("PET_IT_ENABLED"));
        if (!"true".equals(enabled)) {
            throw new IllegalStateException(
                    "IT 未显式启用：外部共享 IT 库模式须以 -Dpet.it.enabled=true 显式触发（防误跑）");
        }
    }

    @AfterAll
    static void stopContainer() {
        if (MYSQL != null) {
            MYSQL.stop();
        }
    }

    /** IT 最小切片：喂食/权益链路 + MyBatis/Flyway/事务，不加载 Web/Nacos/Sentinel/Redis/MQ。 */
    @Configuration
    @MapperScan("com.cloudmart.pet.repository")
    @EnableTransactionManagement
    @Import({
            PetInteractionServiceImpl.class,
            PetStateService.class,
            PetQuotaService.class,
            PetLinkedProductServiceImpl.class,
            PetItemCatalog.class,
            UserPetTimezoneResolver.class,
            com.cloudmart.pet.config.MyBatisPlusConfig.class,
            com.cloudmart.pet.config.PetClock.class
    })
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    static class FeedItSlice {

        @Bean
        java.time.Clock systemClock() {
            return java.time.Clock.systemUTC();
        }

        @Bean
        com.cloudmart.pet.config.PetProperties petProperties() {
            return new com.cloudmart.pet.config.PetProperties();
        }

        @Bean
        com.cloudmart.pet.config.PetMetrics petMetrics() {
            return new com.cloudmart.pet.config.PetMetrics(new SimpleMeterRegistry());
        }
    }

    // ---- 旁路协作者：均不承担 AT04 不变量（边界 mock，非被测内部方法） ----
    @MockitoBean private PetService petService;
    @MockitoBean private PetRankingCache petRankingCache;
    @MockitoBean private PetAchievementService petAchievementService;
    @MockitoBean private PetDailyQuestService petDailyQuestService;
    @MockitoBean private PetIntimacyService petIntimacyService;
    @MockitoBean private PetHomeService petHomeService;
    @MockitoBean private PetOutboxService petOutboxService;
    @MockitoBean private PetFriendFeedService petFriendFeedService;
    @MockitoBean private PetCompanionFeatureService petCompanionFeatureService;
    @MockitoBean private PetCooperationService petCooperationService;
    @MockitoBean private PetActivityMutex petActivityMutex;
    @MockitoBean private CouponFeignClient couponFeignClient;
    @MockitoBean private WishUserProfileFeignClient wishUserProfileFeignClient;

    @Autowired private PetInteractionService interactionService;
    @Autowired private PetMapper petMapper;
    @Autowired private PetInventoryMapper inventoryMapper;
    @Autowired private PetFeedEntitlementMapper entitlementMapper;
    @Autowired private PetActivityMapper activityMapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 高段随机基数，保证并发运行间数据互不碰撞（用户亿级段、主键 9.0e18 段） */
    private final long runSeed = new java.util.Random().nextInt(100_000_000, 900_000_000);
    private static final long PET_ID_MIN = 9_000_000_000_000_000_000L;
    private static final long PET_ID_MAX = 9_000_999_999_999_999_999L;
    private final AtomicLong petIdSeq = new AtomicLong(PET_ID_MIN);
    private final List<Long> testUsers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Long> testPetIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final String FOOD_APPLE = "apple";
    /** apple 效果（V37 种子）：hunger+15 happiness+2 hp+0；双倍 → 30 / 4 */
    private static final int APPLE_HUNGER = 15;
    private static final int APPLE_HAPPINESS = 2;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        // pet 表带 @TableLogic，mapper.delete 为软删（PK 残留会撞后续用例）——测试数据必须物理删除；
        // 统一按本类占用的高段 ID 段清理，同时自愈此前失败运行的残留行。
        jdbcTemplate.update("DELETE FROM pet_activity WHERE pet_id BETWEEN ? AND ?", PET_ID_MIN, PET_ID_MAX);
        jdbcTemplate.update("DELETE FROM pet_inventory WHERE pet_id BETWEEN ? AND ?", PET_ID_MIN, PET_ID_MAX);
        jdbcTemplate.update("DELETE FROM pet_feed_operation WHERE operation_id LIKE 'feed-at04%'");
        jdbcTemplate.update("DELETE FROM pet_feed_entitlement WHERE redemption_code LIKE 'AT04%'");
        jdbcTemplate.update("DELETE FROM pet WHERE id BETWEEN ? AND ?", PET_ID_MIN, PET_ID_MAX);
        testPetIds.clear();
        testUsers.clear();
    }

    // ============================ AT04 用例 ============================

    @Test
    @DisplayName("AT04-a：同喂食键双端并发（同主宠/2 苹果/1 权益）→ 只结算一次、权益只消耗一份")
    void at04a_sameFeedKeyConcurrent_settledExactlyOnce() throws Exception {
        long user = nextUser();
        long petId = seedActivePet(user, 0, 0);
        seedInventory(petId, user, 2);
        long entitlementId = seedEntitlement(user, "AT04A");

        String feedKey = "feed-at04a-" + UUID.randomUUID();
        runConcurrent(2, () -> interactionService.feedItem(user, FOOD_APPLE, feedKey));

        // 结算恰好一次：背包只扣 1 个苹果
        assertThat(inventoryQuantity(petId)).as("同喂食键并发只扣一个食物").isEqualTo(1);
        // 数值只应用一次且为双倍（先占用权益成功）
        Pet pet = petMapper.selectById(petId);
        assertThat(pet.getHunger()).as("双倍饱食只结算一次（15*2=30）").isEqualTo(APPLE_HUNGER * 2);
        assertThat(pet.getHappiness()).as("双倍心情只结算一次（2*2=4）").isEqualTo(APPLE_HAPPINESS * 2);
        // 活动留痕只一条
        assertThat(countFeedActivities(petId)).as("喂食事实只留一条").isEqualTo(1L);
        // 权益恰好一份被该操作占用、归属本宠
        PetFeedEntitlement entitlement = entitlementMapper.selectById(entitlementId);
        assertThat(entitlement.getStatus()).isEqualTo(PetFeedEntitlement.STATUS_CONSUMED);
        assertThat(entitlement.getConsumedByOperationId()).isEqualTo(feedKey);
        assertThat(entitlement.getConsumedPetId()).isEqualTo(petId);
        assertThat(entitlement.getVersion()).isEqualTo(1);
        assertThat(countConsumedEntitlements(user)).as("只占用一份权益").isEqualTo(1L);
        assertThat(countFeedOperations(user, feedKey)).as("喂食操作台账恰好一行").isEqualTo(1L);
    }

    @Test
    @DisplayName("AT04-b：同喂食键串行重放 → 第二次不扣背包/不重复结算，返回幂等成功")
    void at04b_sameFeedKeySequentialReplay_noSecondSettlement() {
        long user = nextUser();
        long petId = seedActivePet(user, 0, 0);
        seedInventory(petId, user, 2);
        long entitlementId = seedEntitlement(user, "AT04B");

        String feedKey = "feed-at04b-" + UUID.randomUUID();
        interactionService.feedItem(user, FOOD_APPLE, feedKey);
        // 客户端重试/另一端稍后提交同一操作键
        interactionService.feedItem(user, FOOD_APPLE, feedKey);

        assertThat(inventoryQuantity(petId)).as("重放不再扣背包").isEqualTo(1);
        Pet pet = petMapper.selectById(petId);
        assertThat(pet.getHunger()).isEqualTo(APPLE_HUNGER * 2);
        assertThat(pet.getHappiness()).isEqualTo(APPLE_HAPPINESS * 2);
        assertThat(countFeedActivities(petId)).as("重放不新增喂食事实").isEqualTo(1L);
        PetFeedEntitlement entitlement = entitlementMapper.selectById(entitlementId);
        assertThat(entitlement.getStatus()).isEqualTo(PetFeedEntitlement.STATUS_CONSUMED);
        assertThat(entitlement.getConsumedByOperationId()).isEqualTo(feedKey);
        assertThat(entitlement.getConsumedPetId()).isEqualTo(petId);
    }

    @Test
    @DisplayName("AT04-c：首端在 P1 结算后切宠，同喂食键打到 P2 → 拒绝/幂等，不串宠不扣 P2 背包")
    void at04c_sameFeedKeyAfterPetSwitch_notSettledOnSecondPet() {
        long user = nextUser();
        long pet1 = seedActivePet(user, 0, 0);
        long pet2 = seedInactivePet(user, 0, 0);
        seedInventory(pet1, user, 2);
        seedInventory(pet2, user, 2);
        long entitlementId = seedEntitlement(user, "AT04C");

        String feedKey = "feed-at04c-" + UUID.randomUUID();
        interactionService.feedItem(user, FOOD_APPLE, feedKey);

        // 模拟"切宠"：主宠切换在 DB 层即对原主宠行的更新（与喂食 FOR UPDATE 互斥，严格串行）
        switchActivePet(user, pet1, pet2);
        interactionService.feedItem(user, FOOD_APPLE, feedKey);

        // P2 完全不发生结算
        assertThat(inventoryQuantity(pet2)).as("切宠后重放不得扣新宠背包").isEqualTo(2);
        Pet p2 = petMapper.selectById(pet2);
        assertThat(p2.getHunger()).as("新宠数值不得被旧操作改变").isZero();
        assertThat(p2.getHappiness()).isZero();
        assertThat(countFeedActivities(pet2)).as("新宠无喂食事实").isZero();
        // 权益仍只归属 P1
        PetFeedEntitlement entitlement = entitlementMapper.selectById(entitlementId);
        assertThat(entitlement.getStatus()).isEqualTo(PetFeedEntitlement.STATUS_CONSUMED);
        assertThat(entitlement.getConsumedPetId()).as("权益不串宠").isEqualTo(pet1);
        assertThat(inventoryQuantity(pet1)).isEqualTo(1);
        assertThat(countFeedActivities(pet1)).isEqualTo(1L);
    }

    @Test
    @DisplayName("AT04-d：两个不同喂食键并发（2 苹果/1 权益）→ 一份双倍+一份普通，占用失败者不获得双倍")
    void at04d_twoDistinctKeys_oneDoubledOneNormal() throws Exception {
        long user = nextUser();
        long petId = seedActivePet(user, 0, 0);
        seedInventory(petId, user, 2);
        seedEntitlement(user, "AT04D");

        String key1 = "feed-at04d-1-" + UUID.randomUUID();
        String key2 = "feed-at04d-2-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = List.of(key1, key2).stream().map(key -> {
            java.util.concurrent.Callable<Object> task = () -> {
                ready.countDown();
                start.await(30, TimeUnit.SECONDS);
                interactionService.feedItem(user, FOOD_APPLE, key);
                return null;
            };
            return pool.submit(task);
        }).toList();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<Object> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 两次都是合法结算：背包扣 2
        assertThat(inventoryQuantity(petId)).as("两个不同操作各扣一个食物").isZero();
        Pet pet = petMapper.selectById(petId);
        // 恰好一次双倍（30）+ 一次普通（15）= 45；心情 4+2=6
        assertThat(pet.getHunger()).as("占用失败方不得获得双倍：30+15=45").isEqualTo(45);
        assertThat(pet.getHappiness()).isEqualTo(6);
        assertThat(countFeedActivities(petId)).as("两次独立喂食各留一条事实").isEqualTo(2L);
        // 唯一一份权益只被其中一个操作占用
        List<PetFeedEntitlement> consumed = entitlementMapper.selectList(
                new LambdaQueryWrapper<PetFeedEntitlement>()
                        .eq(PetFeedEntitlement::getUserId, user)
                        .eq(PetFeedEntitlement::getStatus, PetFeedEntitlement.STATUS_CONSUMED));
        assertThat(consumed).hasSize(1);
        assertThat(consumed.get(0).getConsumedByOperationId()).isIn(key1, key2);
        assertThat(consumed.get(0).getConsumedPetId()).isEqualTo(petId);
    }

    @Test
    @DisplayName("AT04-e：权益按用户隔离——他人喂食不得消耗/占用我的权益")
    void at04e_entitlementNotCrossUser() {
        long owner = nextUser();
        long other = nextUser();
        long ownerPet = seedActivePet(owner, 0, 0);
        long otherPet = seedActivePet(other, 0, 0);
        seedInventory(otherPet, other, 1);
        long entitlementId = seedEntitlement(owner, "AT04E");

        // 他人正常喂食：背包可扣、效果普通，但绝不能碰到 owner 的权益
        interactionService.feedItem(other, FOOD_APPLE, "feed-at04e-" + UUID.randomUUID());

        PetFeedEntitlement entitlement = entitlementMapper.selectById(entitlementId);
        assertThat(entitlement.getStatus()).as("他人喂食不得占用我的权益")
                .isEqualTo(PetFeedEntitlement.STATUS_AVAILABLE);
        assertThat(entitlement.getConsumedByOperationId()).isNull();
        assertThat(entitlement.getConsumedPetId()).isNull();
        Pet fed = petMapper.selectById(otherPet);
        assertThat(fed.getHunger()).as("他人喂食按普通效果结算").isEqualTo(APPLE_HUNGER);
        assertThat(fed.getHappiness()).isEqualTo(APPLE_HAPPINESS);
        assertThat(inventoryQuantity(otherPet)).isZero();
        assertThat(ownerPet).isNotNull();
    }

    @Test
    @DisplayName("AT04-f：同喂食键对应不同食物 → PET_IDEMPOTENCY_CONFLICT，键不得复用")
    void at04f_sameFeedKeyDifferentItem_conflict() {
        long user = nextUser();
        long petId = seedActivePet(user, 0, 0);
        seedInventory(petId, user, 2);
        seedEntitlement(user, "AT04F");

        String feedKey = "feed-at04f-" + UUID.randomUUID();
        interactionService.feedItem(user, FOOD_APPLE, feedKey);
        assertThatThrownBy(() -> interactionService.feedItem(user, "milk", feedKey))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PET_IDEMPOTENCY_CONFLICT"));

        // 冲突请求不产生任何结算：台账仍只一行、背包只扣一次、数值只一次双倍
        assertThat(countFeedOperations(user, feedKey)).isEqualTo(1L);
        assertThat(inventoryQuantity(petId)).isEqualTo(1);
        Pet pet = petMapper.selectById(petId);
        assertThat(pet.getHunger()).isEqualTo(APPLE_HUNGER * 2);
        assertThat(pet.getHappiness()).isEqualTo(APPLE_HAPPINESS * 2);
        assertThat(countFeedActivities(petId)).isEqualTo(1L);
    }

    // ============================ 辅助 ============================

    /** 多线程同构并发：统一发令，全部结束或异常均向上抛出，超时 60s。 */
    private void runConcurrent(int threads, ThrowingFeed action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Object> task = () -> {
            ready.countDown();
            start.await(30, TimeUnit.SECONDS);
            action.run();
            return null;
        };
        List<Future<Object>> futures = IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(task)).toList();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<Object> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    @FunctionalInterface
    private interface ThrowingFeed {
        void run() throws Exception;
    }

    private long nextUser() {
        long userId = runSeed + testUsers.size() + 1L;
        testUsers.add(userId);
        return userId;
    }

    private long seedActivePet(long userId, int hunger, int happiness) {
        return seedPet(userId, hunger, happiness, true);
    }

    private long seedInactivePet(long userId, int hunger, int happiness) {
        return seedPet(userId, hunger, happiness, false);
    }

    private long seedPet(long userId, int hunger, int happiness, boolean active) {
        long petId = petIdSeq.incrementAndGet();
        Pet pet = new Pet();
        pet.setId(petId);
        pet.setUserId(userId);
        pet.setName("IT宠" + (testPetIds.size() + 1));
        pet.setSpecies("STRAWBERRY");
        pet.setPersonality("GENTLE");
        pet.setAppearance("{}");
        pet.setGrowthStage("YOUNG");
        pet.setLevel(1);
        pet.setExp(0);
        pet.setHp(100);
        pet.setMaxHp(100);
        pet.setHunger(hunger);
        pet.setHappiness(happiness);
        pet.setEnergy(100);
        pet.setCleanliness(100);
        pet.setStatus("IDLE");
        pet.setIsActive(active);
        pet.setHungerFrac(0.0);
        pet.setHappinessFrac(0.0);
        pet.setEnergyFrac(0.0);
        pet.setCleanlinessFrac(0.0);
        // 衰减游标取当前时刻，保证用例期间无自然衰减干扰数值断言
        pet.setLastStateUpdateAt(LocalDateTime.now());
        petMapper.insert(pet);
        testPetIds.add(petId);
        return petId;
    }

    private void seedInventory(long petId, long userId, int quantity) {
        PetInventory inventory = new PetInventory();
        inventory.setId(petIdSeq.incrementAndGet());
        inventory.setPetId(petId);
        inventory.setUserId(userId);
        inventory.setItemType(PetItemType.FOOD.name());
        inventory.setItemCode(FOOD_APPLE);
        inventory.setQuantity(quantity);
        inventory.setEquipped(false);
        inventory.setAcquiredAt(LocalDateTime.now());
        inventoryMapper.insert(inventory);
    }

    private long seedEntitlement(long userId, String caseTag) {
        PetFeedEntitlement entitlement = new PetFeedEntitlement();
        entitlement.setUserId(userId);
        entitlement.setRedemptionCode(caseTag + "-" + UUID.randomUUID());
        entitlement.setItemCode(FOOD_APPLE);
        entitlement.setStatus(PetFeedEntitlement.STATUS_AVAILABLE);
        entitlement.setUnitSequence(1);
        entitlement.setVersion(0);
        entitlementMapper.insert(entitlement);
        return entitlement.getId();
    }

    private void switchActivePet(long userId, long fromPetId, long toPetId) {
        Pet from = petMapper.selectById(fromPetId);
        from.setIsActive(false);
        petMapper.updateById(from);
        Pet to = petMapper.selectById(toPetId);
        to.setIsActive(true);
        petMapper.updateById(to);
        // 校验测试前置：函数唯一索引 uk_pet_user_active 允许该状态
        Pet active = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId).eq(Pet::getIsActive, true).last("LIMIT 1"));
        assertThat(active.getId()).isEqualTo(toPetId);
    }

    private Integer inventoryQuantity(long petId) {
        PetInventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, petId)
                .eq(PetInventory::getItemType, PetItemType.FOOD)
                .eq(PetInventory::getItemCode, FOOD_APPLE));
        return inventory == null ? null : inventory.getQuantity();
    }

    private long countFeedActivities(long petId) {
        return activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getPetId, petId)
                .eq(PetActivity::getActivityType, PetActivityType.FEED.name()));
    }

    private long countConsumedEntitlements(long userId) {
        return entitlementMapper.selectCount(new LambdaQueryWrapper<PetFeedEntitlement>()
                .eq(PetFeedEntitlement::getUserId, userId)
                .eq(PetFeedEntitlement::getStatus, PetFeedEntitlement.STATUS_CONSUMED));
    }

    private long countFeedOperations(long userId, String operationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pet_feed_operation WHERE user_id = ? AND operation_id = ?",
                Long.class, userId, operationId);
    }
}
