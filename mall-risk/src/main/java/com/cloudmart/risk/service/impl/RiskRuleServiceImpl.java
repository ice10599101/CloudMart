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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskRuleServiceImpl implements RiskRuleService {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleServiceImpl.class);

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
        audit("create", null, entity);
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
        RiskRule before = copyOf(entity);
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
        audit("update", before, entity);
        return riskConverter.toRiskRuleVO(entity);
    }

    @Override
    public void deleteRule(Long id) {
        RiskRule entity = riskRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("RISK_RULE_NOT_FOUND", "风控规则不存在");
        }
        riskRuleMapper.deleteById(id);
        audit("delete", entity, null);
    }

    /**
     * T02 审计：规则变更直接改变下单/领券/支付的放行结果，必须留下前后差异、服务主体与请求 ID。
     *
     * <p>operator 是服务令牌主体（mall-admin）；提出变更的管理员身份与原因由 mall-admin 的
     * {@code @OperLog} 记录，两者通过同一 {@code requestId}（Feign 透传）关联。</p>
     */
    private void audit(String action, RiskRule before, RiskRule after) {
        log.info("[AUDIT] risk-rule.{} requestId={} operator={} before={} after={}",
                action, MDC.get("requestId"), currentOperator(), snapshot(before), snapshot(after));
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "unknown" : authentication.getName();
    }

    private String snapshot(RiskRule rule) {
        if (rule == null) {
            return "null";
        }
        return "{id=" + rule.getId()
                + ", name=" + rule.getName()
                + ", actionType=" + rule.getActionType()
                + ", riskLevel=" + rule.getRiskLevel()
                + ", threshold=" + rule.getThreshold()
                + ", timeWindowMinutes=" + rule.getTimeWindowMinutes()
                + ", status=" + rule.getStatus()
                + ", priority=" + rule.getPriority()
                + ", description=" + rule.getDescription()
                + "}";
    }

    private RiskRule copyOf(RiskRule source) {
        RiskRule copy = new RiskRule();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setActionType(source.getActionType());
        copy.setRiskLevel(source.getRiskLevel());
        copy.setThreshold(source.getThreshold());
        copy.setTimeWindowMinutes(source.getTimeWindowMinutes());
        copy.setStatus(source.getStatus());
        copy.setPriority(source.getPriority());
        copy.setDescription(source.getDescription());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
