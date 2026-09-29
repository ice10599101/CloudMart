package com.cloudmart.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * ENG-01 余量：上下文装配冒烟——回归 SessionValidator 双构造器在
 * Spring Framework 7 下被回退到默认构造器导致启动即挂的问题。
 * ApplicationContextRunner 只装配目标 Bean，不需要 Redis/网关真实环境。
 */
@DisplayName("SessionValidator 上下文装配")
class SessionValidatorContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SessionValidator.class)
            .withBean(ReactiveStringRedisTemplate.class, () -> mock(ReactiveStringRedisTemplate.class))
            .withPropertyValues("gateway.session.key-prefix=auth:session_valid:");

    @Test
    @DisplayName("多构造器场景下能正确装配（回归：无 @Autowired 曾致启动即挂）")
    void context_loads_validator() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SessionValidator.class);
            assertThat(context.getBean(SessionValidator.class)).isNotNull();
        });
    }

    @Test
    @DisplayName("缺失依赖 Bean 时装配失败（fail-fast 语义验证）")
    void context_missingDependency_fails() {
        new ApplicationContextRunner()
                .withUserConfiguration(SessionValidator.class)
                .withPropertyValues("gateway.session.key-prefix=auth:session_valid:")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }
}
