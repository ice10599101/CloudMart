package com.cloudmart.software.it;

import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.feign.SoftwareFileFeignClient;
import com.cloudmart.software.feign.SoftwareNotificationFeignClient;
import com.cloudmart.software.feign.UserSupportFeignClient;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 软件模块集成测试基类（与 mall-wish IT 同一套双模式约定）。
 *
 * <p>基础设施：默认 Testcontainers 自起真实 MySQL 9 + Redis（Flyway 全量迁移建表）；
 * 显式设置 CLOUDMART_IT_MYSQL_HOST 时走授权环境共享实例（mysql-it:8307 / redis-it:8380），
 * 保留 -Dsoftware.it.enabled=true 显式启用标志与跨运行互斥锁。</p>
 *
 * <p>外部依赖处理：Nacos config/discovery 禁用；RocketMQ 排除自动装配（RocketMQTemplate
 * 以 MockitoBean 替换，Outbox 发送失败本身 Fail-Open）；四个跨服务 Feign 客户端以
 * MockitoBean 替换（跨服务边界属单测/契约测试职责）。</p>
 *
 * <p>隔离策略：每个用例后 TRUNCATE 全部业务表 + FLUSHDB。software_category 为 V5 种子
 * 字典表，TRUNCATE 后 Flyway 不会重跑，故不清理（IT 用例只读分类，不改其状态）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        // Nacos config-data 导入保持解析（shared-runtime.yml 等照常拉取；
        // 数据源/Redis 由 DynamicPropertySource 以更高优先级覆盖为 IT 容器）
        // 仅关闭注册发现与配置监听客户端
        "spring.cloud.nacos.discovery.enabled=false",
        // Sentinel 不急连 dashboard
        "spring.cloud.sentinel.eager=false",
        // 排除 RocketMQ 自动装配（消费者不连 broker）与 Knife4j（非 Web 环境装配失败）
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration,"
                + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration,"
                + "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration"
})
@ActiveProfiles("it")
public abstract class SoftwareIntegrationTestBase {

    /**
     * 双模式（T26 约定）：Testcontainers 独占（默认）／外部共享实例（显式环境变量）。
     */
    static final boolean USE_EXTERNAL_INFRA = System.getenv("CLOUDMART_IT_MYSQL_HOST") != null;

    static final MySQLContainer<?> MYSQL = USE_EXTERNAL_INFRA ? null : new MySQLContainer<>("mysql:9.4.0")
            .withStartupTimeout(Duration.ofMinutes(5));
    static final GenericContainer<?> REDIS = USE_EXTERNAL_INFRA ? null : new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @org.springframework.test.context.DynamicPropertySource
    static void infrastructure(org.springframework.test.context.DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_INFRA) {
            // 外部容器模式：连接信息来自 application-it.yml 的环境变量默认值，不覆盖
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
    static void requireExplicitItEnvironment() {
        // 防误跑守卫仅约束外部共享实例（TRUNCATE/FLUSHDB 会清共享库）——
        // Testcontainers 模式容器独占，无需人工授权
        if (!USE_EXTERNAL_INFRA) {
            return;
        }
        String enabled = System.getProperty("software.it.enabled", System.getenv("CLOUDMART_IT_ENABLED"));
        if (!"true".equals(enabled)) {
            throw new IllegalStateException("IT 未显式启用：请以 -Dsoftware.it.enabled=true 显式触发（防误跑）");
        }
    }

    static {
        // Sentinel 客户端默认写 ~/logs/csp，重定向到 target 避免污染用户目录
        System.setProperty("csp.sentinel.log.dir", "target/sentinel-log");
    }

    // ========== 跨运行互斥锁（仅外部共享实例模式） ==========

    private static final String RUN_LOCK_KEY = "software-it:run-lock";
    /** 锁存 db14：用例的 FLUSHDB 只作用于 db0，锁不会随用例清理丢失 */
    private static final int RUN_LOCK_REDIS_DB = 14;
    /** TTL 大于单次全量 IT 运行时长：JVM 崩溃后锁自动过期自愈 */
    private static final Duration RUN_LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofMinutes(10);
    private static final long ACQUIRE_POLL_INTERVAL_MS = 2000;

    private static String lockToken;
    private static LettuceConnectionFactory lockFactory;
    private static StringRedisTemplate lockTemplate;

    /** 释放锁的原子脚本：仅持有者（token 匹配）可删除，防止误删后到期的他人锁 */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) end return 0",
            Long.class);

    @BeforeAll
    static void acquireRunLock() throws InterruptedException {
        if (!USE_EXTERNAL_INFRA) {
            return; // Testcontainers 独占实例，无跨运行互踩问题
        }
        String host = System.getenv().getOrDefault("CLOUDMART_IT_REDIS_HOST", "129.204.152.168");
        int port = Integer.parseInt(System.getenv().getOrDefault("CLOUDMART_IT_REDIS_PORT", "8380"));
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(RUN_LOCK_REDIS_DB);
        lockFactory = new LettuceConnectionFactory(config);
        lockFactory.afterPropertiesSet();
        lockTemplate = new StringRedisTemplate(lockFactory);
        lockTemplate.afterPropertiesSet();

        lockToken = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + ACQUIRE_TIMEOUT.toNanos();
        while (true) {
            Boolean acquired = lockTemplate.opsForValue()
                    .setIfAbsent(RUN_LOCK_KEY, lockToken, RUN_LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return;
            }
            if (System.nanoTime() - deadline >= 0) {
                throw new IllegalStateException(
                        "集成库运行锁被占用（key=" + RUN_LOCK_KEY + "，可能另一台机器/IDE/命令行正在跑 IT，"
                                + "或上一次运行崩溃后锁尚未到期（TTL " + RUN_LOCK_TTL.toMinutes() + " 分钟）。"
                                + "确认无并发运行后可手动删除该 Key 重试。");
            }
            Thread.sleep(ACQUIRE_POLL_INTERVAL_MS);
        }
    }

    @AfterAll
    static void releaseRunLock() {
        if (!USE_EXTERNAL_INFRA) {
            return;
        }
        if (lockTemplate != null && lockToken != null) {
            lockTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(RUN_LOCK_KEY), lockToken);
        }
        if (lockFactory != null) {
            lockFactory.destroy();
        }
    }

    /** 全部业务表：@AfterEach 统一 TRUNCATE 保证用例隔离。
     *  software_category 不在此列：V5 种子字典表被 TRUNCATE 后 Flyway 不会重跑，
     *  且 IT 用例对分类只读（saveDraft 校验 categoryId 引用 V5 种子）。 */
    private static final List<String> BUSINESS_TABLES = List.of(
            "software", "software_revision", "software_release", "software_source",
            "software_source_state", "software_revision_tag", "software_revision_media",
            "software_tag", "software_user_preference", "software_subscription",
            "software_subscription_delivery", "software_fanout_task", "software_favorite",
            "software_rating", "software_comment", "software_comment_like", "software_report",
            "software_link_feedback", "software_link_access_daily", "software_access_contact",
            "software_access_grant", "software_permission_audit", "software_publisher_permission",
            "software_operation", "software_audit_log", "software_topic", "software_topic_item",
            "software_erasure_task", "compensation_task", "inbox_record", "outbox_event");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @MockitoBean
    protected UserSupportFeignClient userSupportFeignClient;

    @MockitoBean
    protected CommunitySupportFeignClient communitySupportFeignClient;

    @MockitoBean
    protected SoftwareFileFeignClient softwareFileFeignClient;

    @MockitoBean
    protected SoftwareNotificationFeignClient softwareNotificationFeignClient;

    @MockitoBean
    protected RocketMQTemplate rocketMQTemplate;

    @org.junit.jupiter.api.BeforeEach
    void stubExternalCalls() {
        // submit 的远程阶段（9.7）：IT 聚焦持久化边界——审核结论固定通过、账号固定可用
        org.mockito.Mockito.lenient()
                .when(communitySupportFeignClient.review(
                        org.mockito.ArgumentMatchers.any(
                                com.cloudmart.software.dto.software.SoftwareReviewRequest.class)))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                        new com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO(
                                "it", true, false, java.util.List.of(), null,
                                java.time.Instant.now())));
        org.mockito.Mockito.lenient()
                .when(userSupportFeignClient.profiles(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(com.cloudmart.common.api.ApiResponse.ok(java.util.List.of(
                        new com.cloudmart.software.dto.software.SoftwareProfileVO(
                                0L, "it", null, true))));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : BUSINESS_TABLES) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }
}
