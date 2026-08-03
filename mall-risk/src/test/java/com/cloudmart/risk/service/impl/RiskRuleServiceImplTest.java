package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.risk.converter.RiskConverter;
import com.cloudmart.risk.dto.CreateRiskRuleRequest;
import com.cloudmart.risk.dto.UpdateRiskRuleRequest;
import com.cloudmart.risk.entity.RiskRule;
import com.cloudmart.risk.repository.RiskRuleMapper;
import com.cloudmart.risk.vo.RiskRuleVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskRuleServiceImplTest {

    private RiskRuleMapper riskRuleMapper;
    private RiskConverter riskConverter;
    private RiskRuleServiceImpl riskRuleService;

    private static final Long RULE_ID = 1L;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(RiskRule.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.risk.repository");
            TableInfoHelper.initTableInfo(assistant, RiskRule.class);
        }
    }

    @BeforeEach
    void setUp() {
        riskRuleMapper = mock(RiskRuleMapper.class);
        riskConverter = mock(RiskConverter.class);
        riskRuleService = new RiskRuleServiceImpl(riskRuleMapper, riskConverter);
    }

    @Nested
    @DisplayName("createRule")
    class CreateRuleTests {

        @Test
        @DisplayName("should create rule and return VO")
        void createRule_success_returnsVo() {
            CreateRiskRuleRequest request = new CreateRiskRuleRequest(
                    "频繁下单", "ORDER", "HIGH", 10, 5, 0, "5分钟内下单超过10次"
            );

            RiskRuleVO vo = new RiskRuleVO(RULE_ID, "频繁下单", "ORDER", 10, "HIGH", true);

            when(riskConverter.toRiskRuleVO(any(RiskRule.class))).thenReturn(vo);

            RiskRuleVO result = riskRuleService.createRule(request);

            assertThat(result.id()).isEqualTo(RULE_ID);
            assertThat(result.name()).isEqualTo("频繁下单");
            verify(riskRuleMapper).insert(any(RiskRule.class));
        }

        @Test
        @DisplayName("should default status to 0 when status is null")
        void createRule_nullStatus_defaultsToZero() {
            CreateRiskRuleRequest request = new CreateRiskRuleRequest(
                    "异常支付", "PAYMENT", "MEDIUM", 5, 10, null, "10分钟内支付超过5次"
            );

            RiskRuleVO vo = new RiskRuleVO(2L, "异常支付", "PAYMENT", 5, "MEDIUM", true);
            when(riskConverter.toRiskRuleVO(any(RiskRule.class))).thenReturn(vo);

            RiskRuleVO result = riskRuleService.createRule(request);

            assertThat(result.name()).isEqualTo("异常支付");
            verify(riskRuleMapper).insert(any(RiskRule.class));
        }
    }

    @Nested
    @DisplayName("listRules")
    class ListRulesTests {

        @Test
        @DisplayName("should return all rules ordered by id desc")
        void listRules_returnsAllRules() {
            RiskRule rule1 = new RiskRule();
            rule1.setId(2L);
            rule1.setName("规则2");
            RiskRule rule2 = new RiskRule();
            rule2.setId(1L);
            rule2.setName("规则1");

            RiskRuleVO vo1 = new RiskRuleVO(2L, "规则2", "ORDER", 10, "HIGH", true);
            RiskRuleVO vo2 = new RiskRuleVO(1L, "规则1", "PAYMENT", 5, "MEDIUM", false);

            when(riskRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule1, rule2));
            when(riskConverter.toRiskRuleVOList(List.of(rule1, rule2))).thenReturn(List.of(vo1, vo2));

            List<RiskRuleVO> results = riskRuleService.listRules();

            assertThat(results).hasSize(2);
            assertThat(results.getFirst().name()).isEqualTo("规则2");
        }

        @Test
        @DisplayName("should return empty list when no rules exist")
        void listRules_empty_returnsEmptyList() {
            when(riskRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(riskConverter.toRiskRuleVOList(List.of())).thenReturn(List.of());

            List<RiskRuleVO> results = riskRuleService.listRules();

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRule")
    class GetRuleTests {

        @Test
        @DisplayName("should return rule VO when found")
        void getRule_found_returnsVo() {
            RiskRule entity = new RiskRule();
            entity.setId(RULE_ID);
            entity.setName("频繁下单");

            RiskRuleVO vo = new RiskRuleVO(RULE_ID, "频繁下单", "ORDER", 10, "HIGH", true);

            when(riskRuleMapper.selectById(RULE_ID)).thenReturn(entity);
            when(riskConverter.toRiskRuleVO(entity)).thenReturn(vo);

            RiskRuleVO result = riskRuleService.getRule(RULE_ID);

            assertThat(result.id()).isEqualTo(RULE_ID);
            assertThat(result.name()).isEqualTo("频繁下单");
        }

        @Test
        @DisplayName("should throw BusinessException when rule not found")
        void getRule_notFound_throwsBusinessException() {
            when(riskRuleMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> riskRuleService.getRule(RULE_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("风控规则不存在");
        }
    }

    @Nested
    @DisplayName("updateRule")
    class UpdateRuleTests {

        @Test
        @DisplayName("should update rule and return VO when found")
        void updateRule_found_updatesAndReturnsVo() {
            RiskRule entity = new RiskRule();
            entity.setId(RULE_ID);
            entity.setName("频繁下单");
            entity.setActionType("ORDER");
            entity.setRiskLevel("HIGH");
            entity.setThreshold(10);
            entity.setTimeWindowMinutes(5);
            entity.setStatus(0);
            entity.setDescription("5分钟内下单超过10次");

            UpdateRiskRuleRequest request = new UpdateRiskRuleRequest(
                    "频繁下单V2", null, "CRITICAL", 20, null, null, "升级版规则"
            );

            RiskRuleVO vo = new RiskRuleVO(RULE_ID, "频繁下单V2", "ORDER", 20, "CRITICAL", true);

            when(riskRuleMapper.selectById(RULE_ID)).thenReturn(entity);
            when(riskConverter.toRiskRuleVO(entity)).thenReturn(vo);

            RiskRuleVO result = riskRuleService.updateRule(RULE_ID, request);

            assertThat(result.name()).isEqualTo("频繁下单V2");
            verify(riskRuleMapper).updateById(entity);
        }

        @Test
        @DisplayName("should throw BusinessException when rule not found")
        void updateRule_notFound_throwsBusinessException() {
            when(riskRuleMapper.selectById(anyLong())).thenReturn(null);

            UpdateRiskRuleRequest request = new UpdateRiskRuleRequest("新名称", null, null, null, null, null, null);

            assertThatThrownBy(() -> riskRuleService.updateRule(RULE_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("风控规则不存在");
        }

        @Test
        @DisplayName("should only update non-null fields")
        void updateRule_partialUpdate_onlyUpdatesNonNullFields() {
            RiskRule entity = new RiskRule();
            entity.setId(RULE_ID);
            entity.setName("原名称");
            entity.setActionType("ORDER");
            entity.setRiskLevel("HIGH");
            entity.setThreshold(10);
            entity.setTimeWindowMinutes(5);
            entity.setStatus(0);
            entity.setDescription("原描述");

            UpdateRiskRuleRequest request = new UpdateRiskRuleRequest(
                    null, null, null, null, null, 1, null
            );

            RiskRuleVO vo = new RiskRuleVO(RULE_ID, "原名称", "ORDER", 10, "HIGH", false);

            when(riskRuleMapper.selectById(RULE_ID)).thenReturn(entity);
            when(riskConverter.toRiskRuleVO(entity)).thenReturn(vo);

            RiskRuleVO result = riskRuleService.updateRule(RULE_ID, request);

            assertThat(entity.getStatus()).isEqualTo(1);
            assertThat(entity.getName()).isEqualTo("原名称");
            verify(riskRuleMapper).updateById(entity);
        }
    }

    @Nested
    @DisplayName("deleteRule")
    class DeleteRuleTests {

        @Test
        @DisplayName("should delete rule by id")
        void deleteRule_deletesById() {
            riskRuleService.deleteRule(RULE_ID);

            verify(riskRuleMapper).deleteById(anyLong());
        }
    }
}
