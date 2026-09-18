package com.cloudmart.pet.config;

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
 * Sentinel 流控兜底配置。
 *
 * <p>资源命名 {@code PET_{动作}}（与 mall-wish {@code WISH_{动作}} 同约定）：
 * 互动/领取/聊天等写操作 QPS 限制防刷；详细规则经 Nacos {@code mall-pet-flow-rules} 下发。</p>
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

        rules.add(buildRule("PET_QUERY", 30));
        rules.add(buildRule("PET_CREATE", 5));
        rules.add(buildRule("PET_INTERACTION", 10));
        rules.add(buildRule("PET_ACTIVITY_START", 5));
        rules.add(buildRule("PET_ACTIVITY_CLAIM", 10));
        rules.add(buildRule("PET_BOTTLE", 5));
        rules.add(buildRule("PET_BATTLE", 5));
        rules.add(buildRule("PET_CHAT", 5));

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
