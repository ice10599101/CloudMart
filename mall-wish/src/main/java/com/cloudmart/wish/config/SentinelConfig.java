package com.cloudmart.wish.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 流控配置。
 *
 * <p>对应文档第四章 4.1 频率限制与反作弊：</p>
 * <ul>
 *   <li>WISH_CREATE：单用户 QPS ≤ 5（防刷心愿）</li>
 *   <li>WISH_QUERY：列表查询 QPS ≤ 30（保护 cursor 分页性能）</li>
 *   <li>WISH_DETAIL：详情查询 QPS ≤ 60</li>
 *   <li>HOME_AGGREGATION：首页聚合 QPS ≤ 20（保护推荐算法）</li>
 *   <li>WISH_INTERACTION：互动操作 QPS ≤ 10（限频见 InteractionType 枚举注释）</li>
 *   <li>WISH_CHECKIN：打卡 QPS ≤ 5</li>
 *   <li>WISH_AUDIT：审核操作 QPS ≤ 10（管理后台）</li>
 * </ul>
 *
 * <p>详细规则通过 Nacos {@code mall-wish-flow-rules} 动态下发，本类仅提供兜底默认值。</p>
 */
@Configuration
public class SentinelConfig {

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        rules.add(buildRule("WISH_CREATE", 5));
        rules.add(buildRule("WISH_QUERY", 30));
        rules.add(buildRule("WISH_DETAIL", 60));
        rules.add(buildRule("HOME_AGGREGATION", 20));
        rules.add(buildRule("WISH_INTERACTION", 10));
        rules.add(buildRule("WISH_CHECKIN", 5));
        rules.add(buildRule("WISH_AUDIT", 10));

        FlowRuleManager.loadRules(rules);
    }

    private FlowRule buildRule(String resource, int qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setLimitApp("default");
        return rule;
    }
}
