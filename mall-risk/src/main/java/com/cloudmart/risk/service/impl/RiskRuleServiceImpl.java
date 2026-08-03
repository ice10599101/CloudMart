package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.risk.converter.RiskConverter;
import com.cloudmart.risk.dto.CreateRiskRuleRequest;
import com.cloudmart.risk.dto.UpdateRiskRuleRequest;
import com.cloudmart.risk.entity.RiskRule;
import com.cloudmart.risk.repository.RiskRuleMapper;
import com.cloudmart.risk.service.RiskRuleService;
import com.cloudmart.risk.vo.RiskRuleVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskRuleServiceImpl implements RiskRuleService {

    private final RiskRuleMapper riskRuleMapper;
    private final RiskConverter riskConverter;

    public RiskRuleServiceImpl(RiskRuleMapper riskRuleMapper, RiskConverter riskConverter) {
        this.riskRuleMapper = riskRuleMapper;
        this.riskConverter = riskConverter;
    }

    @Override
    public RiskRuleVO createRule(CreateRiskRuleRequest request) {
        RiskRule entity = new RiskRule();
        entity.setName(request.name());
        entity.setActionType(request.actionType());
        entity.setRiskLevel(request.riskLevel());
        entity.setThreshold(request.threshold());
        entity.setTimeWindowMinutes(request.timeWindowMinutes());
        entity.setStatus(request.status() != null ? request.status() : 0);
        entity.setDescription(request.description());
        riskRuleMapper.insert(entity);
        return riskConverter.toRiskRuleVO(entity);
    }

    @Override
    public List<RiskRuleVO> listRules() {
        List<RiskRule> rules = riskRuleMapper.selectList(
                new LambdaQueryWrapper<RiskRule>().orderByDesc(RiskRule::getId)
        );
        return riskConverter.toRiskRuleVOList(rules);
    }

    @Override
    public RiskRuleVO getRule(Long id) {
        RiskRule rule = riskRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("RISK_RULE_NOT_FOUND", "风控规则不存在");
        }
        return riskConverter.toRiskRuleVO(rule);
    }

    @Override
    public RiskRuleVO updateRule(Long id, UpdateRiskRuleRequest request) {
        RiskRule entity = riskRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("RISK_RULE_NOT_FOUND", "风控规则不存在");
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.actionType() != null) {
            entity.setActionType(request.actionType());
        }
        if (request.riskLevel() != null) {
            entity.setRiskLevel(request.riskLevel());
        }
        if (request.threshold() != null) {
            entity.setThreshold(request.threshold());
        }
        if (request.timeWindowMinutes() != null) {
            entity.setTimeWindowMinutes(request.timeWindowMinutes());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        riskRuleMapper.updateById(entity);
        return riskConverter.toRiskRuleVO(entity);
    }

    @Override
    public void deleteRule(Long id) {
        riskRuleMapper.deleteById(id);
    }
}
