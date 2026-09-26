package com.cloudmart.wish.it;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.service.AssistantAiClient;
import com.cloudmart.wish.service.TreeHoleAiClient;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 集成测试基类（Sprint 1.4）。
 *
 * <p>基础设施：docker-compose.yml 中 mysql-it(8307)/redis-it(8380) 专用容器，
 * 走真实 MySQL 9 / Redis（与生产同版本），Flyway 全量迁移建表。
 * 业务实例(8306/8379)与开发数据完全隔离。</p>
 *
 * <p>外部依赖处理原则：
 * <ul>
 *   <li>Nacos config/discovery：测试属性禁用（optional import 不依赖远程配置中心）</li>
 *   <li>RocketMQ：排除 auto-configuration（消费者不连 broker，避免误消费业务 Topic），
 *       RocketMQTemplate 以 MockitoBean 替换（生产者发送失败本身 Fail-Open）</li>
 *   <li>mall-user Feign：MockitoBean 替换（跨服务边界属单测/契约测试职责）</li>
 *   <li>大模型 API：TreeHoleAiClient 以 MockitoBean 替换（外部付费 API，且危机链路
 *       需断言"未外发"）</li>
 *   <li>Sentinel：关闭 eager 上报（规则源为空时注解直接放行，不影响链路覆盖）</li>
 * </ul></p>
 *
 * <p>隔离策略：每个用例后 TRUNCATE 全部业务表 + FLUSHDB（redis-it 为专用实例，
 * FLUSHDB 不影响业务 Redis）。context 在同 profile 测试类间缓存复用。</p>
 *
 * <p><b>跨运行互斥（缺陷修复）</b>：mysql-it/redis-it 为共享实例，且本用例集的
 * 隔离手段是 TRUNCATE/FLUSHDB——同一时刻只能有一个测试 JVM 使用，否则会出现
 * 两类互相踩踏：A 运行 @AfterEach TRUNCATE 清掉 B 运行正在使用的种子数据
 * （Duplicate entry / EmptyResult），以及 Flyway 新迁移 DDL 与在途事务冲突
 * （Table definition has changed / Deadlock）。因此 @BeforeAll 先经 redis-it
 * 的 db14（用例只 FLUSHDB db0，锁不会被打扫）获取带 TTL 的运行锁，
 * 拿不到锁则在超时内等待，避免两台机器/IDE+命令行并发跑 IT 互相污染。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        // 断开 Nacos config import（optional 但避免连接重试拖慢启动）
        "spring.config.import=",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        // Sentinel 不急连 dashboard
        "spring.cloud.sentinel.eager=false",
        // 排除 RocketMQ 自动装配（合并主配置中已排除的 OAuth2 ResourceServer）；
        // Knife4j 必须排除：非 Web 环境(NONE)下 springdoc 自动配置被 @ConditionalOnWebApplication
        // 跳过，而 Knife4jAutoConfiguration 仍尝试创建 knife4jOpenApiCustomizer 并注入
        // SpringDocConfigProperties → NoSuchBeanDefinitionException（集成测试无 HTTP 层）
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration,"
                + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration,"
                + "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration"
})
@ActiveProfiles("it")
public abstract class WishIntegrationTestBase {

    @BeforeAll
    static void requireExplicitItEnvironment() {
        // B23：IT 必须显式启用（-Dwish.it.enabled=true）并显式提供本地隔离库地址；
        // 缺任一拒绝执行——防止单测阶段误连业务库/远程共享库
        String enabled = System.getProperty("wish.it.enabled", System.getenv("WISH_IT_ENABLED"));
        if (!"true".equals(enabled)) {
            throw new IllegalStateException("IT 未显式启用：需 -Dwish.it.enabled=true 且配置本地隔离库"
                    + "（WISH_IT_MYSQL_HOST 等），禁止默认连远程实例（B23）");
        }
        String host = System.getProperty("wish.it.mysql-host", System.getenv("WISH_IT_MYSQL_HOST"));
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("缺少 WISH_IT_MYSQL_HOST：IT 必须指向显式声明的本地临时库（B23）");
        }
    }

    static {
        // Sentinel 客户端默认写 ~/logs/csp，重定向到 target 避免污染用户目录
        // （同时规避沙箱/CI 环境对用户主目录的写限制）
        System.setProperty("csp.sentinel.log.dir", "target/sentinel-log");
    }

    // ========== 跨运行互斥锁（与 application-it.yml 的 redis-it 同源） ==========

    private static final String RUN_LOCK_KEY = "wish-it:run-lock";
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
        String host = System.getenv().getOrDefault("WISH_IT_REDIS_HOST", "129.204.152.168");
        int port = Integer.parseInt(System.getenv().getOrDefault("WISH_IT_REDIS_PORT", "8380"));
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
        if (lockTemplate != null && lockToken != null) {
            lockTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(RUN_LOCK_KEY), lockToken);
        }
        if (lockFactory != null) {
            lockFactory.destroy();
        }
    }

    /** 全部业务表：@AfterEach 统一 TRUNCATE 保证用例隔离。
     *  注意 wish_badge 不在此列：V1 种子的字典表被 TRUNCATE 后 Flyway 不会
     *  重跑，改由 {@link #ensureBadgeSeedData} 幂等补种；wish_env_config 虽然
     *  被 TRUNCATE，但由 {@link #ensureEnvConfigSeedData} 每用例前幂等补种
     *  恢复 16 条种子（管理端 CRUD 用例可放心改库，is_active 强制复位） */
    private static final List<String> BUSINESS_TABLES = List.of(
            "wish", "wish_progress", "wish_interaction", "wish_growth_record",
            "wish_checkin", "wish_user_stat", "wish_resource_log", "wish_daily_signin",
            "wish_signin_milestone_claim", "wish_user_badge", "wish_category",
            "wish_comment", "wish_consent",
            "wish_ai_conversation", "wish_world_tree_state", "wish_fulfillment",
            "wish_special_event", "wish_env_config", "time_capsule",
            "wish_ai_goal", "wish_notification_preference", "wish_ai_prompt",
            "wish_expected_at_action", "wish_ai_config", "wish_bgm_song",
            "wish_match_group", "wish_match_member", "wish_match_config",
            "wish_fulfillment_inherit", "wish_content_flow_log", "wish_leaderboard_config",
            "wish_grayscale_config", "wish_ai_review",
            "wish_warm_event", "wish_fence", "wish_fence_arrival",
            "wish_encounter_letter", "wish_encounter_letter_interaction",
            "wish_lbs_suspicious", "wish_lbs_freeze", "wish_live_widget_config",
            "wish_activity", "wish_activity_participant", "wish_activity_reward_log",
            "wish_virtual_asset", "wish_user_asset", "wish_brand",
            "wish_brand_pool", "wish_brand_pool_member",
            "wish_collection", "wish_data_export",
            "wish_drift_bottle", "wish_drift_bottle_comment",
            "wish_drift_bottle_interaction", "wish_drift_bottle_fish_log",
            "wish_gift", "wish_gift_record");

    /** 内容流转/年度报告异步线程池：@AfterEach 清库前必须先静默（见 cleanUp） */
    @Autowired
    @Qualifier("contentFlowExecutor")
    private ThreadPoolTaskExecutor contentFlowExecutor;

    @Autowired
    @Qualifier("annualReportExecutor")
    private ThreadPoolTaskExecutor annualReportExecutor;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected com.cloudmart.wish.service.GrayscaleService grayscaleService;

    @MockitoBean
    protected UserFeignClient userFeignClient;

    @MockitoBean
    protected TreeHoleAiClient treeHoleAiClient;

    @MockitoBean
    protected AssistantAiClient assistantAiClient;

    @MockitoBean
    protected RocketMQTemplate rocketMQTemplate;

    /**
     * 用例隔离第二步：清库前先等 @Async 后台任务（内容流转重试/年度报告生成）排空。
     *
     * <p>背景：submitFulfillment 事务提交后会异步触发内容流转（独立线程池 +
     * 指数退避重试，单任务最长约 3.5s）。若任务在 TRUNCATE 之后才提交写库，
     * 会与下一用例的种子数据交错（Duplicate/死锁/ER_TABLE_DEF_CHANGED 1412）。
     * 等待有界（15s），超时则放行继续清理——最坏退化为该用例可能残留后台写入，
     * 不让单个卡死任务拖垮整个测试类。</p>
     */
    @AfterEach
    void cleanUp() {
        awaitExecutorQuiescence("contentFlowExecutor", contentFlowExecutor);
        awaitExecutorQuiescence("annualReportExecutor", annualReportExecutor);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : BUSINESS_TABLES) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    /** 等待线程池活跃任务与队列排空；timeout 到期放行（可观测：FAIL 前打点留给人工） */
    private static void awaitExecutorQuiescence(String name, ThreadPoolTaskExecutor executor) {
        if (executor == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + 15_000L;
        while ((executor.getActiveCount() > 0 || executor.getQueueSize() > 0)
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 幂等补种 V1 徽章种子 + V5 rarity（ON DUPLICATE KEY UPDATE）。
     *
     * <p>自愈场景：wish_badge 一旦被误 TRUNCATE（如首轮 Badge 集成的隔离策略
     * 缺陷），Flyway 不会重跑 V1/V5，种子永久丢失。每次用例前补种保证定义
     * 恒在（含 rarity，与 V1+V5 合并态一致）；集成库被人工清库后亦自动恢复。</p>
     */
    @BeforeEach
    void ensureBadgeSeedData() {
        jdbcTemplate.update("""
                INSERT INTO wish_badge (id, code, name, icon, rarity, is_active, `condition`) VALUES
                    (2001, 'FIRST_WISH',    '第一次许愿', '', 'COMMON', 1,
                     JSON_OBJECT('type', 'WISH_CREATED', 'threshold', 1, 'description', '发布第一个心愿')),
                    (2002, 'FIRST_FULFILL', '第一次还愿', '', 'COMMON', 1,
                     JSON_OBJECT('type', 'WISH_FULFILLED', 'threshold', 1, 'description', '完成第一个还愿')),
                    (2003, 'HELP_100',      '帮助100人', '', 'EPIC', 1,
                     JSON_OBJECT('type', 'TOTAL_HELPED', 'threshold', 100, 'description', '累计帮助100人(点亮+匿名星光)')),
                    (2004, 'PERSIST_365',   '坚持365天', '', 'LEGENDARY', 1,
                     JSON_OBJECT('type', 'TOTAL_CHECKIN_DAYS', 'threshold', 365, 'description', '累计打卡365天'))
                ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `rarity` = VALUES(`rarity`),
                    `is_active` = VALUES(`is_active`), `condition` = VALUES(`condition`)
                """);
    }

    /**
     * 幂等补种环境配置种子（16 条，ON DUPLICATE KEY UPDATE）。
     *
     * <p>wish_env_config 被 TRUNCATE 后 Flyway 不会重跑 V10/V11，种子由本方法
     * 每用例前补种恢复（管理端 CRUD 用例可放心改库）；与 wish_badge
     * 补种策略一致。种子内容与 V11 修复迁移保持同步。</p>
     *
     * <p>id 必须显式提供：雪花主键无 AUTO_INCREMENT，裸 INSERT 报
     * "Field 'id' doesn't have a default value"（V10 种子即栽在此）。
     * 字典表小整数 id 3101-3116，与 V11 同口径。</p>
     */
    @BeforeEach
    void ensureEnvConfigSeedData() {
        jdbcTemplate.update("""
                INSERT INTO wish_env_config (id, env_code, category, name, description, priority, visual) VALUES
                    (3101, 'SUNNY',         'WEATHER', '晴天',   '晴空万里，树心暖金光晕', 50,
                     JSON_OBJECT('skyColor', '#87ceeb', 'lightCoreColor', '#ffd700', 'particle', 'NONE')),
                    (3102, 'CLOUDY',        'WEATHER', '多云',   '云层柔和，树影朦胧', 50,
                     JSON_OBJECT('skyColor', '#9aa5b1', 'lightCoreColor', '#cfd8dc', 'particle', 'NONE')),
                    (3103, 'RAIN',          'WEATHER', '下雨',   '细雨润泽，果实微光涟漪', 50,
                     JSON_OBJECT('skyColor', '#5d737e', 'lightCoreColor', '#4facfe', 'particle', 'RAIN')),
                    (3104, 'SNOW',          'WEATHER', '下雪',   '落雪覆枝，冬夜静谧', 50,
                     JSON_OBJECT('skyColor', '#7a8ba3', 'lightCoreColor', '#bfe8ff', 'particle', 'SNOWFLAKE')),
                    (3105, 'RAINBOW',       'WEATHER', '彩虹',   '雨后初霁，七彩拱桥横跨树冠（情绪联动触发）', 80,
                     JSON_OBJECT('skyColor', '#6c5ce7', 'lightCoreColor', '#ff9ff3', 'particle', 'NONE')),
                    (3106, 'SPRING',        'SEASON',  '春季',   '嫩绿花瓣飘落', 30,
                     JSON_OBJECT('crownColor', '#7ef0c0', 'particle', 'PETAL')),
                    (3107, 'SUMMER',        'SEASON',  '夏季',   '绿叶阳光斑驳', 30,
                     JSON_OBJECT('crownColor', '#3ddc97', 'particle', 'SUNBURST')),
                    (3108, 'AUTUMN',        'SEASON',  '秋季',   '金黄落叶纷飞', 30,
                     JSON_OBJECT('crownColor', '#ffb347', 'particle', 'LEAF')),
                    (3109, 'WINTER',        'SEASON',  '冬季',   '枯枝雪花点缀', 30,
                     JSON_OBJECT('crownColor', '#bfe8ff', 'particle', 'SNOWFLAKE')),
                    (3110, 'DAY',           'TIME',    '白天',   '晨光清朗（06-12 时）', 10,
                     JSON_OBJECT('skyColor', '#87ceeb')),
                    (3111, 'DUSK',          'TIME',    '黄昏',   '暮色橙霞（12-18 时）', 10,
                     JSON_OBJECT('skyColor', '#ff9a76')),
                    (3112, 'NIGHT',         'TIME',    '夜晚',   '星幕初垂（18-24 时）', 10,
                     JSON_OBJECT('skyColor', '#0c1b3a')),
                    (3113, 'LATE_NIGHT',    'TIME',    '深夜',   '万籁俱寂（00-06 时）', 10,
                     JSON_OBJECT('skyColor', '#060b18')),
                    (3114, 'METEOR_SHOWER', 'SPECIAL_EVENT', '流星雨', '全站流星划过树冠，愿望随星而落', 100,
                     JSON_OBJECT('skyColor', '#0c1b3a', 'lightCoreColor', '#ffd700', 'particle', 'METEOR')),
                    (3115, 'AURORA',        'SPECIAL_EVENT', '极光',   '极光绸缎萦绕世界树', 100,
                     JSON_OBJECT('skyColor', '#0a1f2e', 'lightCoreColor', '#7ef0c0', 'particle', 'AURORA')),
                    (3116, 'STAR_NIGHT',    'SPECIAL_EVENT', '星辰夜', '满天星辰为愿望加冕', 100,
                     JSON_OBJECT('skyColor', '#0c1b3a', 'lightCoreColor', '#ffd700', 'particle', 'STAR'))
                ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `category` = VALUES(`category`),
                    `priority` = VALUES(`priority`), `visual` = VALUES(`visual`),
                    `is_active` = 1, `description` = VALUES(`description`)
                """);
    }

    /**
     * 幂等补种 AI/提醒策略配置种子（5 条，ON DUPLICATE KEY UPDATE）。
     *
     * <p>wish_ai_config 被 TRUNCATE 后 Flyway 不会重跑 V13，种子由本方法
     * 每用例前补种恢复（陪伴提醒/预期管理限频用例可放心改库后复位）；
     * 与 wish_badge / wish_env_config 补种策略一致，内容与 V13 同口径。</p>
     */
    @BeforeEach
    void ensureAiConfigSeedData() {
        jdbcTemplate.update("""
                INSERT INTO wish_ai_config (id, config_key, config_value, description) VALUES
                    (1, 'reminder.daily_limit', '1', '陪伴提醒单用户每日上限(条)'),
                    (2, 'reminder.quiet_start', '22:00', '免打扰时段开始(用户时区, HH:mm)'),
                    (3, 'reminder.quiet_end', '08:00', '免打扰时段结束(用户时区, HH:mm)'),
                    (4, 'expected.daily_limit', '3', '预期管理通知单用户每日上限(条)'),
                    (5, 'annual_report.ttl_hours', '168', '年度报告结果缓存时长(小时)')
                ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`),
                    `description` = VALUES(`description`)
                """);
    }

    /**
     * 幂等补种 V15 匹配算法配置种子（7 条，与 V15 同口径）。
     *
     * <p>wish_match_config 纳入 TRUNCATE 后 Flyway 不重跑 V15，
     * 每用例前补种保证权重/阈值/限频配置恒在（管理端改配置的用例
     * 可放心改库，每用例前复位为默认值）。</p>
     */
    @BeforeEach
    void ensureMatchConfigSeedData() {
        jdbcTemplate.update("""
                INSERT INTO wish_match_config (id, config_key, config_value, description) VALUES
                    (1, 'match.weight_keyword', '0.4', '匹配权重-关键词(0-1)'),
                    (2, 'match.weight_city', '0.3', '匹配权重-城市/geohash同城(0-1)'),
                    (3, 'match.weight_activity', '0.3', '匹配权重-小组成员活跃度(0-1)'),
                    (4, 'match.score_threshold', '0.15', '推荐相似度阈值(0-1)'),
                    (5, 'match.remind_idle_days', '3', '互相提醒-idle天数(天)'),
                    (6, 'match.remind_daily_limit', '3', '互相提醒-每日提醒上限(条)'),
                    (7, 'match.create_daily_limit', '3', '建组-每日建组上限(个)')
                ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`),
                    `description` = VALUES(`description`)
                """);
    }

    /**
     * 幂等补种 V16 排行榜配置种子（4 条，与 V16 同口径）。
     */
    @BeforeEach
    void ensureLeaderboardConfigSeedData() {
        jdbcTemplate.update("""
                INSERT INTO wish_leaderboard_config (id, config_key, config_value, description) VALUES
                    (1, 'lb.refresh_minutes', '10', '榜单刷新周期(分钟)'),
                    (2, 'lb.top_size', '100', '每榜单保留 Top N'),
                    (3, 'lb.tiebreak', 'CREATED_AT_ASC', '同分处理: 早在前'),
                    (4, 'lb.exclude_restricted', '1', '排除风控受限用户(1=排除)')
                ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`),
                    `description` = VALUES(`description`)
                """);
    }

    /**
     * 让用户信息 Feign 调用返回稳定占位数据（昵称填充链路不依赖 mall-user）。
     */
    protected void stubUserFeign() {
        when(userFeignClient.getUserById(anyLong()))
                .thenReturn(ApiResponse.ok(Map.of(
                        "id", 1L, "nickname", "测试用户", "avatar", "")));
        when(userFeignClient.batchGetUsers(anyList()))
                .thenReturn(ApiResponse.ok(List.of()));
    }

    /**
     * 种子数据：插入心愿分类（createWish 会校验分类存在性）。
     *
     * <p>幂等：uk_category_code 冲突时更新名称，重复调用（如外部并发跑测试
     * 导致前次清理未完成）不抛 DuplicateKey。</p>
     *
     * @param code 分类编码
     * @return 分类 ID
     */
    protected Long seedCategory(String code) {
        jdbcTemplate.update(
                "INSERT INTO wish_category (id, code, name, sort, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 0, NOW(), NOW()) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name), sort = VALUES(sort)",
                // BIGINT UNSIGNED 列：nanoTime 可能为负，掩掉符号位保证非负
                System.nanoTime() & 0x7FFFFFFFFFFFFFFFL, code, "测试分类-" + code);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM wish_category WHERE code = ?", Long.class, code);
    }

    /**
     * 种子数据：插入用户统计（LIGHT 互动需星光余额）。
     *
     * <p>幂等：主键冲突时更新余额，防止共享 it-库上清理未完成时
     * 种子 DuplicateKey 级联导致整个用例类失败。</p>
     */
    protected void seedUserStat(long userId, int starlightBalance) {
        jdbcTemplate.update(
                "INSERT INTO wish_user_stat (user_id, starlight_balance, created_at, updated_at) "
                        + "VALUES (?, ?, NOW(), NOW()) "
                        + "ON DUPLICATE KEY UPDATE starlight_balance = VALUES(starlight_balance), "
                        + "updated_at = NOW()",
                userId, starlightBalance);
    }
}
