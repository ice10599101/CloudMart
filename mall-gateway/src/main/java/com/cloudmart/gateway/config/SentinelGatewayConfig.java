package com.cloudmart.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentinel 网关流控配置。
 * 定义各路由的 QPS 限流规则和统一熔断响应格式。
 */
@Configuration
@ConditionalOnProperty(name = "sentinel.gateway.rules.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelGatewayConfig {

    @PostConstruct
    public void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 秒杀路由 - 严格限流
        rules.add(new GatewayFlowRule("mall-seckill")
                .setCount(100)
                .setIntervalSec(1));

        // 订单路由 - 中等限流
        rules.add(new GatewayFlowRule("mall-order")
                .setCount(200)
                .setIntervalSec(1));

        // 认证路由 - 严格限流防暴力破解
        rules.add(new GatewayFlowRule("mall-auth")
                .setCount(50)
                .setIntervalSec(1));

        // 商品路由 - 较高配额 (浏览为主)
        rules.add(new GatewayFlowRule("mall-product")
                .setCount(500)
                .setIntervalSec(1));

        // 支付路由 - 严格限流
        rules.add(new GatewayFlowRule("mall-payment")
                .setCount(50)
                .setIntervalSec(1));

        // 购物车路由
        rules.add(new GatewayFlowRule("mall-cart")
                .setCount(300)
                .setIntervalSec(1));

        // AI 导购路由 - LLM 成本高，严格限流
        rules.add(new GatewayFlowRule("mall-ai")
                .setCount(20)
                .setIntervalSec(1));

        // 直播路由
        rules.add(new GatewayFlowRule("mall-live")
                .setCount(200)
                .setIntervalSec(1));

        // 营销路由
        rules.add(new GatewayFlowRule("mall-marketing")
                .setCount(100)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);

        // === 熔断降级规则 ===
        // 使用 DegradeRuleManager 加载标准降级规则，资源名与网关路由 ID 一致
        List<DegradeRule> degradeRules = new ArrayList<>();

        // 认证服务 - 异常比例 30%，熔断 15 秒
        degradeRules.add(new DegradeRule("mall-auth")
                .setGrade(1).setCount(0.3)
                .setTimeWindow(15)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000));

        // 支付服务 - 异常比例 30%，熔断 15 秒
        degradeRules.add(new DegradeRule("mall-payment")
                .setGrade(1).setCount(0.3)
                .setTimeWindow(15)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000));

        // 订单服务 - 异常比例 50%，熔断 10 秒
        degradeRules.add(new DegradeRule("mall-order")
                .setGrade(1).setCount(0.5)
                .setTimeWindow(10)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000));

        // 秒杀服务 - 异常比例 50%，熔断 10 秒
        degradeRules.add(new DegradeRule("mall-seckill")
                .setGrade(1).setCount(0.5)
                .setTimeWindow(10)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000));

        // 商品服务 - 异常比例 50%，熔断 10 秒
        degradeRules.add(new DegradeRule("mall-product")
                .setGrade(1).setCount(0.5)
                .setTimeWindow(10)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000));

        // 用户服务 - 异常比例 50%，熔断 10 秒
        degradeRules.add(new DegradeRule("mall-user")
                .setGrade(1).setCount(0.5)
                .setTimeWindow(10)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000));

        // AI 服务 - 异常比例 60%，熔断 10 秒（LLM 不稳定，阈值放宽）
        degradeRules.add(new DegradeRule("mall-ai")
                .setGrade(1).setCount(0.6)
                .setTimeWindow(10)
                .setMinRequestAmount(3)
                .setStatIntervalMs(10000));

        DegradeRuleManager.loadRules(degradeRules);

        // 统一熔断响应格式（符合标准信封）
        String json = "{\"success\":false,\"data\":{},\"error\":{"
                + "\"code\":\"GATEWAY_RATE_LIMITED\","
                + "\"message\":\"请求过于频繁，请稍后再试\","
                + "\"details\":[]},\"meta\":{}}";

        GatewayCallbackManager.setBlockHandler((exchange, t) ->
                ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(json)
        );
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler(
            List<ViewResolver> viewResolvers,
            ServerCodecConfigurer serverCodecConfigurer) {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @Bean("sentinel-json-gw-flow-converter")
    public Converter<String, Set<GatewayFlowRule>> jsonGwFlowConverter() {
        ObjectMapper mapper = new ObjectMapper();
        return source -> {
            try {
                return mapper.readValue(source, new TypeReference<Set<GatewayFlowRule>>() {});
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse gw-flow rules", e);
            }
        };
    }
}
