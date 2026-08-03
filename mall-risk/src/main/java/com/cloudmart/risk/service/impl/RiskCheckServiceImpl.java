package com.cloudmart.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.risk.converter.RiskConverter;
import com.cloudmart.risk.dto.RiskCheckRequest;
import com.cloudmart.risk.dto.RiskCheckResponse;
import com.cloudmart.risk.dto.RiskRecordDTO;
import com.cloudmart.risk.entity.RiskRecord;
import com.cloudmart.risk.entity.RiskRule;
import com.cloudmart.risk.repository.RiskRecordMapper;
import com.cloudmart.risk.repository.RiskRuleMapper;
import com.cloudmart.risk.service.BlacklistService;
import com.cloudmart.risk.service.RiskCheckService;
import com.cloudmart.risk.service.RiskRecordService;
import com.cloudmart.risk.vo.RiskCheckVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RiskCheckServiceImpl implements RiskCheckService {

    private final RiskRuleMapper riskRuleMapper;
    private final RiskRecordMapper riskRecordMapper;
    private final RiskRecordService riskRecordService;
    private final RiskConverter riskConverter;
    private final BlacklistService blacklistService;

    public RiskCheckServiceImpl(RiskRuleMapper riskRuleMapper,
                                RiskRecordMapper riskRecordMapper,
                                RiskRecordService riskRecordService,
                                RiskConverter riskConverter,
                                BlacklistService blacklistService) {
        this.riskRuleMapper = riskRuleMapper;
        this.riskRecordMapper = riskRecordMapper;
        this.riskRecordService = riskRecordService;
        this.riskConverter = riskConverter;
        this.blacklistService = blacklistService;
    }

    @Override
    public RiskCheckVO check(RiskCheckRequest request) {
        Long userId = request.userId();
        String actionType = request.actionType();

        if (blacklistService.isBlacklisted("USER", String.valueOf(userId))) {
            RiskRecordDTO recordDTO = new RiskRecordDTO(
                    null, userId, actionType, "HIGH", "REJECT", null, "用户在黑名单中", null, null
            );
            riskRecordService.createRecord(recordDTO);

            RiskCheckResponse response = new RiskCheckResponse(userId, actionType, "HIGH", "REJECT", null, "用户在黑名单中");
            RiskCheckVO vo = riskConverter.toRiskCheckVO(response);
            return new RiskCheckVO(vo.passed(), vo.riskLevel(), vo.reason(), null);
        }

        List<RiskRule> rules = riskRuleMapper.selectList(
                new LambdaQueryWrapper<RiskRule>()
                        .eq(RiskRule::getActionType, actionType)
                        .eq(RiskRule::getStatus, 0)
        );

        String riskLevel = "LOW";
        String result = "PASS";
        Long triggeredRuleId = null;
        String detail = "无风险";
        String ruleName = null;

        for (RiskRule rule : rules) {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(rule.getTimeWindowMinutes());
            Long count = riskRecordMapper.selectCount(
                    new LambdaQueryWrapper<RiskRecord>()
                            .eq(RiskRecord::getUserId, userId)
                            .eq(RiskRecord::getActionType, actionType)
                            .ge(RiskRecord::getCreatedAt, windowStart)
            );

            if (count >= rule.getThreshold()) {
                riskLevel = rule.getRiskLevel();
                triggeredRuleId = rule.getId();
                ruleName = rule.getName();
                detail = "触发规则: " + rule.getName() + ", 时间窗口内操作次数: " + count + ", 阈值: " + rule.getThreshold();

                if ("HIGH".equals(riskLevel)) {
                    result = "REJECT";
                } else {
                    result = "REVIEW";
                }
                break;
            }
        }

        RiskRecordDTO recordDTO = new RiskRecordDTO(
                null, userId, actionType, riskLevel, result, triggeredRuleId, detail, null, null
        );
        riskRecordService.createRecord(recordDTO);

        RiskCheckResponse response = new RiskCheckResponse(userId, actionType, riskLevel, result, triggeredRuleId, detail);
        RiskCheckVO vo = riskConverter.toRiskCheckVO(response);
        return new RiskCheckVO(vo.passed(), vo.riskLevel(), vo.reason(), ruleName);
    }
}
