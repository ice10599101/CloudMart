package com.cloudmart.coupon.it;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.converter.CouponTemplateConverter;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.feign.RiskFeignClient;
import com.cloudmart.coupon.repository.CouponClaimCounterMapper;
import com.cloudmart.coupon.repository.CouponTemplateMapper;
import com.cloudmart.coupon.repository.ExchangeCodeMapper;
import com.cloudmart.coupon.repository.UserCouponMapper;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.service.ExchangeCodeService;
import com.cloudmart.coupon.service.impl.CouponServiceImpl;
import com.cloudmart.coupon.service.impl.ExchangeCodeServiceImpl;
import com.cloudmart.coupon.util.CodeGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AT01 / AT02（R28-4c）兑换码兑换并发安全真实 MySQL 9 + Redis 集成测试。
 *
 * <p>被测链路 {@code ExchangeCodeService.exchange} 三道防线（真实中间件验证）：
 * <ul>
 *   <li>Redis BitMap SETBIT 快路径：同码并发仅一个线程看到旧值 0；</li>
 *   <li>DB CAS {@code UPDATE exchange_codes ... WHERE status='UNUSED'}：
 *       BitMap 丢失/重建时权威兜底，同一兑换码绝不二次发放（AT01 防超发）；</li>
 *   <li>coupon_claim_counter 原子条件递增（{@code claimed_count < per_user_limit}）
 *       + Redisson 每人每模板锁：同一用户持多码并发兑换恰好成功 1 次（AT02 一人一兑）；
 *       coupon_templates.remaining_quantity 条件递减保证库存不为负。</li>
 * </ul>
 * 所有断言以数据库最终状态为准（码归属、user_coupons 行数、库存、计数台账），
 * 而非仅看返回值。</p>
 *
 * <h3>基础设施双模式</h3>
 * <ul>
 *   <li>默认：Testcontainers 自起 mysql:9.4.0 + redis:7.4（CI runner 自带 Docker）；</li>
 *   <li>外部模式：{@code COUPON_IT_MYSQL_HOST} 直连授权环境 mysql-it(8307)/
 *       redis-it(8380)，schema {@code mall_coupon_it} 与业务库隔离，
 *       须 {@code -Dcoupon.it.enabled=true}。每运行使用随机高段用户 ID、
 *       全新自增模板，@AfterEach 按 ID 精确清理，不做 TRUNCATE/FLUSHDB，
 *       多次运行互不冲突。</li>
 * </ul>
 *
 * <p>外部依赖边界：RiskFeignClient（跨服务风控）以 MockitoBean 桩替换为放行；
 * 不启动 Web/Nacos/Sentinel/Seata。</p>
 */
@SpringBootTest(
        classes = ExchangeCodeConcurrencyIntegrationTest.ItSlice.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.eager=false"
        })
@ActiveProfiles("it")
class ExchangeCodeConcurrencyIntegrationTest {

    private static final boolean USE_EXTERNAL_INFRA = System.getenv("COUPON_IT_MYSQL_HOST") != null;

    static final MySQLContainer<?> MYSQL = USE_EXTERNAL_INFRA ? null
            : new MySQLContainer<>("mysql:9.4.0").withStartupTimeout(Duration.ofMinutes(5));
    static final GenericContainer<?> REDIS = USE_EXTERNAL_INFRA ? null : new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_INFRA) {
            return;
        }
        MYSQL.start();
        REDIS.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeAll
    static void requireExplicitExternalEnablement() {
        if (!USE_EXTERNAL_INFRA) {
            return;
        }
        String enabled = System.getProperty("coupon.it.enabled", System.getenv("COUPON_IT_ENABLED"));
        if (!"true".equals(enabled)) {
            throw new IllegalStateException(
                    "IT 未显式启用：外部共享 IT 库模式须以 -Dcoupon.it.enabled=true 显式触发（防误跑）");
        }
    }

    @AfterAll
    static void stopContainers() {
        if (MYSQL != null) {
            MYSQL.stop();
        }
        if (REDIS != null) {
            REDIS.stop();
        }
    }

    /** IT 最小切片：兑换/领券链路 + MyBatis/Flyway/事务/Redis，不加载 Web/Nacos/Seata。 */
    @Configuration
    @MapperScan("com.cloudmart.coupon.repository")
    @EnableTransactionManagement
    // 复用生产 MyBatis-Plus 配置：实体 createdAt/updatedAt 自动填充 + 分页插件
    @org.springframework.context.annotation.Import(
            com.cloudmart.coupon.config.MyBatisPlusConfig.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    static class ItSlice {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory redisConnectionFactory(Environment environment) {
            String host = environment.getProperty("spring.data.redis.host", "127.0.0.1");
            int port = environment.getProperty("spring.data.redis.port", Integer.class, 6379);
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        /** Redisson 分布式锁是 AT02 串行化的一部分，必须连真实 Redis 构建 */
        @Bean(destroyMethod = "shutdown")
        RedissonClient redissonClient(Environment environment) {
            String host = environment.getProperty("spring.data.redis.host", "127.0.0.1");
            int port = environment.getProperty("spring.data.redis.port", Integer.class, 6379);
            Config config = new Config();
            config.useSingleServer().setAddress("redis://" + host + ":" + port);
            return Redisson.create(config);
        }

        @Bean
        CodeGenerator codeGenerator() {
            return new CodeGenerator();
        }

        @Bean
        UserCouponConverter userCouponConverter() {
            return new com.cloudmart.coupon.converter.UserCouponConverterImpl();
        }

        @Bean
        CouponTemplateConverter couponTemplateConverter() {
            return new com.cloudmart.coupon.converter.CouponTemplateConverterImpl();
        }

        @Bean
        CouponService couponService(CouponTemplateMapper couponTemplateMapper,
                                   UserCouponMapper userCouponMapper,
                                   CouponClaimCounterMapper claimCounterMapper,
                                   RiskFeignClient riskFeignClient,
                                   CouponTemplateConverter couponTemplateConverter,
                                   UserCouponConverter userCouponConverter,
                                   RedissonClient redissonClient,
                                   StringRedisTemplate redisTemplate) {
            return new CouponServiceImpl(couponTemplateMapper, userCouponMapper, claimCounterMapper,
                    riskFeignClient, couponTemplateConverter, userCouponConverter,
                    redissonClient, redisTemplate);
        }

        @Bean
        ExchangeCodeServiceImpl exchangeCodeService(ExchangeCodeMapper exchangeCodeMapper,
                                                    CouponTemplateMapper couponTemplateMapper,
                                                    CouponService couponService,
                                                    CodeGenerator codeGenerator,
                                                    StringRedisTemplate redisTemplate) {
            return new ExchangeCodeServiceImpl(exchangeCodeMapper, couponTemplateMapper,
                    couponService, codeGenerator, redisTemplate);
        }
    }

    @Autowired
    private ExchangeCodeService exchangeCodeService;

    @Autowired
    private CodeGenerator codeGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private RiskFeignClient riskFeignClient;

    /** 每次运行随机高段用户号，外部共享 IT 库多运行互不冲突。 */
    private final long userBase = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
    private final AtomicLong userOffset = new AtomicLong();
    private final List<Long> testUsers = new ArrayList<>();
    private final List<Long> testTemplates = new ArrayList<>();

    @BeforeEach
    void stubRiskAllow() {
        // RISK-01：IT 不跨服务，风控固定放行；REJECT 分支属于风控契约测试
        when(riskFeignClient.check(any()))
                .thenReturn(ApiResponse.ok(Map.of("result", "ALLOW")));
    }

    @AfterEach
    void cleanScopedData() {
        if (!testTemplates.isEmpty()) {
            String tids = joinIds(testTemplates);
            jdbcTemplate.update("DELETE FROM user_coupons WHERE template_id IN (" + tids + ")");
            jdbcTemplate.update("DELETE FROM coupon_claim_counter WHERE template_id IN (" + tids + ")");
            jdbcTemplate.update("DELETE FROM exchange_codes WHERE template_id IN (" + tids + ")");
            jdbcTemplate.update("DELETE FROM coupon_templates WHERE id IN (" + tids + ")");
            for (Long tid : testTemplates) {
                // 兑换码 BitMap/序列号/库存缓存 + 每人每模板分布式锁
                Set<String> lockKeys = redisTemplate.keys("lock:coupon:claim:*:" + tid);
                if (lockKeys != null && !lockKeys.isEmpty()) {
                    redisTemplate.delete(lockKeys);
                }
                redisTemplate.delete(List.of(
                        "exchange:bitmap:" + tid, "exchange:seq:" + tid, "coupon:stock:" + tid));
            }
        }
        testTemplates.clear();
        testUsers.clear();
    }

    // ============================ AT01：同一兑换码防超发 ============================

    /**
     * AT01 核心：30 个不同用户同时兑换同一张码。
     * BitMap 快路径 + DB CAS 双保险下恰好 1 人成功，其余 29 人 EXCHANGE_CODE_ALREADY_USED；
     * 码行最终 EXCHANGED 且归属唯一赢家，库存只扣 1、台账只落 1 行。
     */
    @Test
    void at01_oneCode_thirtyUsers_exactlyOneRedeemed() throws Exception {
        long templateId = seedTemplate(100, 1);
        List<String> codes = seedExchangeCodes(templateId, 1);
        String code = codes.getFirst();
        List<Long> users = newUsers(30);

        List<Outcome> outcomes = runConcurrently(30, () -> {
            long userId = nextUser(users);
            exchangeCodeService.exchange(userId, code);
            return userId;
        });

        long success = outcomes.stream().filter(Outcome::success).count();
        assertThat(success).as("同一兑换码 30 人并发：恰好 1 人兑换成功").isEqualTo(1);
        List<Outcome> losers = outcomes.stream().filter(o -> !o.success()).toList();
        assertThat(losers).hasSize(29).allSatisfy(o ->
                assertThat(o.code()).isEqualTo("EXCHANGE_CODE_ALREADY_USED"));

        Long winnerId = outcomes.stream().filter(Outcome::success).map(Outcome::userId).findFirst().orElseThrow();
        Map<String, Object> codeRow = jdbcTemplate.queryForMap(
                "SELECT status, user_id FROM exchange_codes WHERE code = ?", code);
        assertThat(codeRow.get("status")).isEqualTo("EXCHANGED");
        assertThat(((Number) codeRow.get("user_id")).longValue()).isEqualTo(winnerId);
        assertThat(countUserCoupons(templateId)).as("仅落一张用户券").isEqualTo(1L);
        assertThat(remainingQuantity(templateId)).as("库存恰好扣减 1").isEqualTo(99);
        assertThat(claimedCount(winnerId, templateId)).as("赢家计数台账=1").isEqualTo(1);
    }

    /**
     * AT01 库存维度：模板库存 5，30 用户各持 30 张不同码并发兑换——
     * 恰好 5 张成功（库存绝不超发、remaining 永不为负）；25 张码在领券失败后
     * 由补偿链路恢复 UNUSED，错误码 STOCK_INSUFFICIENT，不产生券与计数残留。
     */
    @Test
    void at01_stockFive_thirtyCodes_exactlyFiveRedeemedNoOversell() throws Exception {
        long templateId = seedTemplate(5, 1);
        List<String> codes = seedExchangeCodes(templateId, 30);
        List<Long> users = newUsers(30);

        List<Outcome> outcomes = runConcurrently(30, () -> {
            long userId = nextUser(users);
            String code = nextCode(codes);
            exchangeCodeService.exchange(userId, code);
            return userId;
        });

        long success = outcomes.stream().filter(Outcome::success).count();
        assertThat(success).as("库存 5：30 张码并发兑换恰好成功 5 张").isEqualTo(5);
        List<Outcome> losers = outcomes.stream().filter(o -> !o.success()).toList();
        assertThat(losers).hasSize(25).allSatisfy(o ->
                assertThat(o.code()).isEqualTo("STOCK_INSUFFICIENT"));

        assertThat(remainingQuantity(templateId)).as("库存扣到 0，绝不出现负数").isZero();
        assertThat(countUserCoupons(templateId)).isEqualTo(5L);
        assertThat(countExchangeCodesByStatus(templateId, "EXCHANGED"))
                .as("恰好 5 张码保持已兑换").isEqualTo(5L);
        assertThat(countExchangeCodesByStatus(templateId, "UNUSED"))
                .as("25 张失败码经补偿恢复为 UNUSED").isEqualTo(25L);
        Long counterRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coupon_claim_counter WHERE template_id = ?", Long.class, templateId);
        assertThat(counterRows).as("失败者的计数行随领券事务整体回滚，仅 5 个赢家有台账").isEqualTo(5L);
        Long overCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coupon_claim_counter WHERE template_id = ? AND claimed_count <> 1",
                Long.class, templateId);
        assertThat(overCount).as("每人限领 1：所有计数台账必须恰好为 1").isZero();
    }

    // ============================ AT02：一人一兑 ============================

    /**
     * AT02 核心：同一用户持 20 张不同码并发兑换同一模板。
     * Redisson 每人每模板锁 + DB 计数台账条件递增双保险下恰好 1 张成功，
     * 其余 19 张 CLAIM_LIMIT_EXCEEDED 并经补偿恢复 UNUSED；
     * user_coupons 唯一一行、claimed_count 恒为 1。
     */
    @Test
    void at02_oneUser_twentyCodes_concurrentExactlyOneClaimed() throws Exception {
        long templateId = seedTemplate(100, 1);
        List<String> codes = seedExchangeCodes(templateId, 20);
        long user = newUser();

        List<Outcome> outcomes = runConcurrently(20, () -> {
            exchangeCodeService.exchange(user, nextCode(codes));
            return user;
        });

        long success = outcomes.stream().filter(Outcome::success).count();
        assertThat(success).as("同一用户 20 张码并发：仅 1 张兑换成功").isEqualTo(1);
        List<Outcome> losers = outcomes.stream().filter(o -> !o.success()).toList();
        // 拒绝允许两种系统定义的业务形态：
        //  - CLAIM_LIMIT_EXCEEDED：抢到 Redisson 锁后被 DB 计数台账条件递增拒绝（权威判定）；
        //  - CLAIM_TOO_FREQUENT：锁等待 3s 仍未排入（高 RTT/极端突发下的节流，码随即补偿恢复）。
        // 一人一兑的安全不变量不依赖拒绝文案，以下方 DB 最终状态硬断言为准；
        // 任何 5xx/EXCHANGE_FAILED/死锁均不被接受。
        assertThat(losers).hasSize(19).allSatisfy(o -> assertThat(o.code())
                .as("只允许限领或节流两种业务拒绝，不允许系统错误/死锁")
                .isIn("CLAIM_LIMIT_EXCEEDED", "CLAIM_TOO_FREQUENT"));

        assertThat(countUserCouponsForUser(user, templateId)).as("用户券恰好 1 张").isEqualTo(1L);
        assertThat(claimedCount(user, templateId)).as("计数台账恒为 1").isEqualTo(1);
        assertThat(countExchangeCodesByStatus(templateId, "EXCHANGED")).isEqualTo(1L);
        assertThat(countExchangeCodesByStatus(templateId, "UNUSED"))
                .as("19 张失败码补偿恢复 UNUSED").isEqualTo(19L);
        Long exchangedToOther = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exchange_codes WHERE template_id = ? AND status = 'EXCHANGED' "
                        + "AND user_id <> ?", Long.class, templateId, user);
        assertThat(exchangedToOther).as("唯一已兑换码必须归属该用户").isZero();
    }

    /** AT02 串行复现：首张码成功后，第二张码立即被 CLAIM_LIMIT_EXCEEDED 拒绝，码保持 UNUSED。 */
    @Test
    void at02_sameUser_secondCodeSequentially_rejected() {
        long templateId = seedTemplate(100, 1);
        List<String> codes = seedExchangeCodes(templateId, 2);
        long user = newUser();

        exchangeCodeService.exchange(user, codes.get(0));
        assertThatThrownBy(() -> exchangeCodeService.exchange(user, codes.get(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("CLAIM_LIMIT_EXCEEDED");

        assertThat(countUserCouponsForUser(user, templateId)).isEqualTo(1L);
        assertThat(claimedCount(user, templateId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM exchange_codes WHERE code = ?", String.class, codes.get(1)))
                .as("被拒码不得占用").isEqualTo("UNUSED");
    }

    // ============================ 兑换码状态机防护 ============================

    /** 已作废/不存在/格式非法码：明确业务拒绝，不触碰库存与台账。 */
    @Test
    void exchange_disabledMissingMalformed_rejected() {
        long templateId = seedTemplate(100, 1);
        List<String> codes = seedExchangeCodes(templateId, 2);
        long user = newUser();

        jdbcTemplate.update("UPDATE exchange_codes SET status = 'DISABLED' WHERE code = ?", codes.get(0));
        assertThatThrownBy(() -> exchangeCodeService.exchange(user, codes.get(0)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("EXCHANGE_CODE_DISABLED");

        String syntacticallyValidButUnknown = codeGenerator.generate(888_888_888L);
        assertThatThrownBy(() -> exchangeCodeService.exchange(user, syntacticallyValidButUnknown))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("EXCHANGE_CODE_NOT_FOUND");

        assertThatThrownBy(() -> exchangeCodeService.exchange(user, "BAD-FORMAT"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_CODE_FORMAT");

        assertThat(countUserCoupons(templateId)).isZero();
        assertThat(remainingQuantity(templateId)).as("非法兑换不扣库存").isEqualTo(100);
    }

    /** 码已兑换后任何用户（含赢家本人）再次兑换均被拒：BitMap 快路径与 DB CAS 各跑一遍。 */
    @Test
    void exchange_alreadyRedeemed_rejectedForEveryone() {
        long templateId = seedTemplate(100, 1);
        String code = seedExchangeCodes(templateId, 1).getFirst();
        long winner = newUser();
        long other = newUser();

        exchangeCodeService.exchange(winner, code);
        assertThatThrownBy(() -> exchangeCodeService.exchange(other, code))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("EXCHANGE_CODE_ALREADY_USED");
        assertThatThrownBy(() -> exchangeCodeService.exchange(winner, code))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("EXCHANGE_CODE_ALREADY_USED");

        assertThat(countUserCoupons(templateId)).as("重放不产生第二张券").isEqualTo(1L);
        assertThat(remainingQuantity(templateId)).isEqualTo(99);
    }

    // ============================ 辅助 ============================

    private long seedTemplate(int totalQuantity, int perUserLimit) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO coupon_templates
                        (name, type, threshold_amount, discount_amount, discount_rate,
                         total_quantity, remaining_quantity, per_user_limit,
                         validity_type, valid_days, status)
                    VALUES ('IT-并发券', 'AMOUNT_OFF', 0.00, 5.00, NULL, ?, ?, ?,
                            'FIXED_DAYS', 30, 'ENABLED')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, totalQuantity);
            ps.setInt(2, totalQuantity);
            ps.setInt(3, perUserLimit);
            return ps;
        }, keyHolder);
        // BIGINT UNSIGNED 经 Connector/J 回传为 BigInteger，用 Number 统一取值
        long templateId = keyHolder.getKey().longValue();
        testTemplates.add(templateId);
        return templateId;
    }

    private List<String> seedExchangeCodes(long templateId, int quantity) {
        // 序列号在模板内从 1 开始；模板 ID 每次运行全新自增，BitMap 键天然不撞
        List<String> codes = new ArrayList<>(quantity);
        for (int seq = 1; seq <= quantity; seq++) {
            String code = codeGenerator.generate(seq);
            jdbcTemplate.update("""
                    INSERT INTO exchange_codes (code, template_id, serial_number, status, exchange_batch)
                    VALUES (?, ?, ?, 'UNUSED', 'it-concurrency')
                    """, code, templateId, seq);
            codes.add(code);
        }
        return codes;
    }

    private long newUser() {
        long id = userBase + userOffset.incrementAndGet();
        testUsers.add(id);
        return id;
    }

    private List<Long> newUsers(int count) {
        List<Long> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            users.add(newUser());
        }
        return users;
    }

    /** 每个工作线程取走唯一一个用户（队列式），失败重试也不复用他人身份 */
    private long nextUser(List<Long> users) {
        synchronized (users) {
            return users.removeFirst();
        }
    }

    /** 每个工作线程取走唯一一张码；调用方只需一次，故不加锁（并发容器队列化） */
    private String nextCode(List<String> codes) {
        synchronized (codes) {
            return codes.removeFirst();
        }
    }

    private long countUserCoupons(long templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons WHERE template_id = ?", Long.class, templateId);
    }

    private long countUserCouponsForUser(long userId, long templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND template_id = ?",
                Long.class, userId, templateId);
    }

    private int remainingQuantity(long templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT remaining_quantity FROM coupon_templates WHERE id = ?",
                Integer.class, templateId);
    }

    private int claimedCount(long userId, long templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT claimed_count FROM coupon_claim_counter WHERE user_id = ? AND template_id = ?",
                Integer.class, userId, templateId);
        return count == null ? 0 : count;
    }

    private long countExchangeCodesByStatus(long templateId, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exchange_codes WHERE template_id = ? AND status = ?",
                Long.class, templateId, status);
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
    }

    /** 闸门并发：同时发起，收集成功用户/失败错误码，不吞异常。 */
    private List<Outcome> runConcurrently(int concurrency, ThrowingExchange action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, 30));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        ConcurrentLinkedQueue<Outcome> outcomes = new ConcurrentLinkedQueue<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        long userId = action.run();
                        outcomes.add(Outcome.ok(userId));
                    } catch (BusinessException be) {
                        outcomes.add(Outcome.fail(null, be.getCode()));
                    } catch (Throwable t) {
                        outcomes.add(Outcome.fail(null, t.getClass().getSimpleName() + ":" + t.getMessage()));
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
    private interface ThrowingExchange {
        long run();
    }

    private record Outcome(boolean success, Long userId, String code) {
        static Outcome ok(Long userId) {
            return new Outcome(true, userId, null);
        }

        static Outcome fail(Long userId, String code) {
            return new Outcome(false, userId, code);
        }
    }
}
