package com.cloudmart.wish.it;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.service.TreeHoleAiClient;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

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
 *   <li>DashScope：TreeHoleAiClient 以 MockitoBean 替换（外部付费 API，且危机链路
 *       需断言"未外发"）</li>
 *   <li>Sentinel：关闭 eager 上报（规则源为空时注解直接放行，不影响链路覆盖）</li>
 * </ul></p>
 *
 * <p>隔离策略：每个用例后 TRUNCATE 全部业务表 + FLUSHDB（redis-it 为专用实例，
 * FLUSHDB 不影响业务 Redis）。context 在同 profile 测试类间缓存复用。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        // 断开 Nacos config import（optional 但避免连接重试拖慢启动）
        "spring.config.import=",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        // Sentinel 不急连 dashboard
        "spring.cloud.sentinel.eager=false",
        // 排除 RocketMQ 自动装配（合并主配置中已排除的 OAuth2 ResourceServer）
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration,"
                + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration"
})
@ActiveProfiles("it")
public abstract class WishIntegrationTestBase {

    static {
        // Sentinel 客户端默认写 ~/logs/csp，重定向到 target 避免污染用户目录
        // （同时规避沙箱/CI 环境对用户主目录的写限制）
        System.setProperty("csp.sentinel.log.dir", "target/sentinel-log");
    }

    /** 全部业务表：@AfterEach 统一 TRUNCATE 保证用例隔离 */
    private static final List<String> BUSINESS_TABLES = List.of(
            "wish", "wish_progress", "wish_interaction", "wish_growth_record",
            "wish_checkin", "wish_user_stat", "wish_resource_log", "wish_badge",
            "wish_user_badge", "wish_category", "wish_comment", "wish_consent",
            "wish_ai_conversation");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @MockitoBean
    protected UserFeignClient userFeignClient;

    @MockitoBean
    protected TreeHoleAiClient treeHoleAiClient;

    @MockitoBean
    protected RocketMQTemplate rocketMQTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : BUSINESS_TABLES) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
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
     * @return 分类 ID
     */
    protected Long seedCategory(String code) {
        jdbcTemplate.update(
                "INSERT INTO wish_category (id, code, name, sort, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 0, NOW(), NOW())",
                System.nanoTime(), code, "测试分类-" + code);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM wish_category WHERE code = ?", Long.class, code);
    }

    /**
     * 种子数据：插入用户统计（LIGHT 互动需星光余额）。
     */
    protected void seedUserStat(long userId, int starlightBalance) {
        jdbcTemplate.update(
                "INSERT INTO wish_user_stat (user_id, starlight_balance, created_at, updated_at) "
                        + "VALUES (?, ?, NOW(), NOW())",
                userId, starlightBalance);
    }
}
