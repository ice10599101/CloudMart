package com.cloudmart.risk.service;

import com.cloudmart.risk.dto.CreateRiskRuleRequest;
import com.cloudmart.risk.dto.UpdateRiskRuleRequest;
import com.cloudmart.risk.vo.RiskRuleVO;

import java.util.List;

public interface RiskRuleService {

    RiskRuleVO createRule(CreateRiskRuleRequest request);

    List<RiskRuleVO> listRules();

    RiskRuleVO getRule(Long id);

    RiskRuleVO updateRule(Long id, UpdateRiskRuleRequest request);

    void deleteRule(Long id);
}
