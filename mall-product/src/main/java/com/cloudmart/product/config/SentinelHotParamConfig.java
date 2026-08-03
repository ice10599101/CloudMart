package com.cloudmart.product.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * 商品服务热点参数防护配置。
 * 对单个商品ID的查询进行限流，防止热点商品拖垮数据库。
 */
@Configuration
public class SentinelHotParamConfig {

    private static final int HOT_PRODUCT_MAX_QPS = 100;
    private static final int DEFAULT_MAX_QPS = 500;

    @PostConstruct
    public void initHotParamRules() {
        // 获取商品详情的热点参数限流：按 productId 参数值限流
        ParamFlowRule getProductRule = new ParamFlowRule("getProductById")
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(DEFAULT_MAX_QPS);

        // 热点商品例外项：单个商品ID允许更高 QPS
        getProductRule.setParamFlowItemList(Collections.emptyList());

        // 搜索接口的热点参数限流：按关键词限流
        ParamFlowRule searchRule = new ParamFlowRule("searchProducts")
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(HOT_PRODUCT_MAX_QPS);

        ParamFlowRuleManager.loadRules(Collections.singletonList(getProductRule));
    }
}
