package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.risk.converter.RiskConverter;
import com.cloudmart.risk.dto.RiskCheckRequest;
import com.cloudmart.risk.dto.RiskCheckResponse;
import com.cloudmart.risk.dto.RiskRecordDTO;
import com.cloudmart.risk.entity.RiskRecord;
import com.cloudmart.risk.entity.RiskRule;
import com.cloudmart.risk.repository.RiskRecordMapper;
import com.cloudmart.risk.repository.RiskRuleMapper;
import com.cloudmart.risk.service.BlacklistService;
import com.cloudmart.risk.service.RiskRecordService;
import com.cloudmart.risk.vo.RiskCheckVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskCheckServiceImplTest {

    private RiskRuleMapper riskRuleMapper;
    private RiskRecordMapper riskRecordMapper;
    private RiskRecordService riskRecordService;
    private RiskConverter riskConverter;
    private BlacklistService blacklistService;
    private RiskCheckServiceImpl riskCheckService;

    private static final Long USER_ID = 1L;
    private static final String ACTION_TYPE = "ORDER_CREATE";

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> entityClass : List.of(RiskRule.class, RiskRecord.class)) {
            if (TableInfoHelper.getTableInfo(entityClass) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.risk.repository");
                TableInfoHelper.initTableInfo(assistant, entityClass);
            }
        }
    }

    @BeforeEach
    void setUp() {
        riskRuleMapper = mock(RiskRuleMapper.class);
        riskRecordMapper = mock(RiskRecordMapper.class);
        riskRecordService = mock(RiskRecordService.class);
        riskConverter = mock(RiskConverter.class);
        blacklistService = mock(BlacklistService.class);
        riskCheckService = new RiskCheckServiceImpl(
                riskRuleMapper, riskRecordMapper, riskRecordService, riskConverter, blacklistService);
        when(blacklistService.isBlacklisted(any(), any())).thenReturn(false);
    }

    @Nested
    @DisplayName("check - pass case")
    class CheckPassTests {

        @Test
        @DisplayName("should pass when no rules exist for action type")
        void check_noRules_returnsPass() {
            RiskCheckRequest request = new RiskCheckRequest(USER_ID, ACTION_TYPE);

            when(riskRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            RiskCheckVO converterVO = new RiskCheckVO(true, "LOW", "无风险", null);
            when(riskConverter.toRiskCheckVO(any(RiskCheckResponse.class))).thenReturn(converterVO);

            RiskCheckVO result = riskCheckService.check(request);

            assertThat(result.passed()).isTrue();
            assertThat(result.riskLevel()).isEqualTo("LOW");
            assertThat(result.ruleName()).isNull();
            verify(riskRecordService).createRecord(any(RiskRecordDTO.class));
        }

        @Test
        @DisplayName("should pass when rule exists but threshold not reached")
        void check_ruleNotTriggered_returnsPass() {
            RiskCheckRequest request = new RiskCheckRequest(USER_ID, ACTION_TYPE);

            RiskRule rule = new RiskRule();
            rule.setId(1L);
            rule.setName("订单频率限制");
            rule.setActionType(ACTION_TYPE);
            rule.setRiskLevel("MEDIUM");
            rule.setThreshold(10);
            rule.setTimeWindowMinutes(60);
            rule.setStatus(0);

            when(riskRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(rule));
            when(riskRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            RiskCheckVO converterVO = new RiskCheckVO(true, "LOW", "无风险", null);
            when(riskConverter.toRiskCheckVO(any(RiskCheckResponse.class))).thenReturn(converterVO);

            RiskCheckVO result = riskCheckService.check(request);

            assertThat(result.passed()).isTrue();
            assertThat(result.riskLevel()).isEqualTo("LOW");
        }
    }

    @Nested
    @DisplayName("check - fail case: high risk rule triggered")
    class CheckHighRiskTests {

        @Test
        @DisplayName("should reject when high risk rule threshold is reached")
        void check_highRiskRuleTriggered_returnsReject() {
            RiskCheckRequest request = new RiskCheckRequest(USER_ID, ACTION_TYPE);

            RiskRule rule = new RiskRule();
            rule.setId(1L);
            rule.setName("高频下单限制");
            rule.setActionType(ACTION_TYPE);
            rule.setRiskLevel("HIGH");
            rule.setThreshold(5);
            rule.setTimeWindowMinutes(60);
            rule.setStatus(0);

            when(riskRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(rule));
            when(riskRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            RiskCheckVO converterVO = new RiskCheckVO(false, "HIGH", "触发规则: 高频下单限制", null);
            when(riskConverter.toRiskCheckVO(any(RiskCheckResponse.class))).thenReturn(converterVO);

            RiskCheckVO result = riskCheckService.check(request);

            assertThat(result.passed()).isFalse();
            assertThat(result.riskLevel()).isEqualTo("HIGH");
            assertThat(result.ruleName()).isEqualTo("高频下单限制");
            verify(riskRecordService).createRecord(any(RiskRecordDTO.class));
        }
    }

    @Nested
    @DisplayName("check - fail case: medium risk rule triggered")
    class CheckMediumRiskTests {

        @Test
        @DisplayName("should review when medium risk rule threshold is reached")
        void check_mediumRiskRuleTriggered_returnsReview() {
            RiskCheckRequest request = new RiskCheckRequest(USER_ID, ACTION_TYPE);

            RiskRule rule = new RiskRule();
            rule.setId(2L);
            rule.setName("订单频率警告");
            rule.setActionType(ACTION_TYPE);
            rule.setRiskLevel("MEDIUM");
            rule.setThreshold(3);
            rule.setTimeWindowMinutes(30);
            rule.setStatus(0);

            when(riskRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(rule));
            when(riskRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            RiskCheckVO converterVO = new RiskCheckVO(false, "MEDIUM", "触发规则: 订单频率警告", null);
            when(riskConverter.toRiskCheckVO(any(RiskCheckResponse.class))).thenReturn(converterVO);

            RiskCheckVO result = riskCheckService.check(request);

            assertThat(result.passed()).isFalse();
            assertThat(result.riskLevel()).isEqualTo("MEDIUM");
            assertThat(result.ruleName()).isEqualTo("订单频率警告");
        }
    }

    @Nested
    @DisplayName("check - multiple rules")
    class CheckMultipleRulesTests {

        @Test
        @DisplayName("should stop at first triggered rule")
        void check_multipleRules_stopsAtFirstTriggered() {
            RiskCheckRequest request = new RiskCheckRequest(USER_ID, ACTION_TYPE);

            RiskRule mediumRule = new RiskRule();
            mediumRule.setId(1L);
            mediumRule.setName("频率警告");
            mediumRule.setActionType(ACTION_TYPE);
            mediumRule.setRiskLevel("MEDIUM");
            mediumRule.setThreshold(3);
            mediumRule.setTimeWindowMinutes(30);
            mediumRule.setStatus(0);

            RiskRule highRule = new RiskRule();
            highRule.setId(2L);
            highRule.setName("高频限制");
            highRule.setActionType(ACTION_TYPE);
            highRule.setRiskLevel("HIGH");
            highRule.setThreshold(10);
            highRule.setTimeWindowMinutes(60);
            highRule.setStatus(0);

            when(riskRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(mediumRule, highRule));
            when(riskRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            RiskCheckVO converterVO = new RiskCheckVO(false, "MEDIUM", "触发规则: 频率警告", null);
            when(riskConverter.toRiskCheckVO(any(RiskCheckResponse.class))).thenReturn(converterVO);

            RiskCheckVO result = riskCheckService.check(request);

            assertThat(result.riskLevel()).isEqualTo("MEDIUM");
            assertThat(result.ruleName()).isEqualTo("频率警告");
        }
    }
}
