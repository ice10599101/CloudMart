package com.cloudmart.community.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelConfig {

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule postCreateRule = new FlowRule();
        postCreateRule.setResource("POST_CREATE");
        postCreateRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        postCreateRule.setCount(5);
        postCreateRule.setLimitApp("default");
        rules.add(postCreateRule);

        FlowRule commentCreateRule = new FlowRule();
        commentCreateRule.setResource("COMMENT_CREATE");
        commentCreateRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        commentCreateRule.setCount(10);
        commentCreateRule.setLimitApp("default");
        rules.add(commentCreateRule);

        FlowRule likeRule = new FlowRule();
        likeRule.setResource("POST_LIKE");
        likeRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        likeRule.setCount(20);
        likeRule.setLimitApp("default");
        rules.add(likeRule);

        FlowRule feedRule = new FlowRule();
        feedRule.setResource("FEED_QUERY");
        feedRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        feedRule.setCount(30);
        feedRule.setLimitApp("default");
        rules.add(feedRule);

        FlowRule searchRule = new FlowRule();
        searchRule.setResource("SEARCH_QUERY");
        searchRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        searchRule.setCount(10);
        searchRule.setLimitApp("default");
        rules.add(searchRule);

        FlowRule reportRule = new FlowRule();
        reportRule.setResource("REPORT_CREATE");
        reportRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        reportRule.setCount(5);
        reportRule.setLimitApp("default");
        rules.add(reportRule);

        FlowRuleManager.loadRules(rules);
    }
}
