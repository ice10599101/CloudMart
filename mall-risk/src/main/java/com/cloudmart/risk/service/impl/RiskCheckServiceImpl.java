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
import com.cloudmart.risk.vo.RiskRecordVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RiskCheckServiceImpl implements RiskCheckService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RiskCheckServiceImpl.class);

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

        // RISK-01：规则按 priority 升序 + id 稳定排序（原遍历命中首个，顺序依赖
        // DB 返回不稳定——同输入可能得出不同结论）
        List<RiskRule> rules = riskRuleMapper.selectList(
                new LambdaQueryWrapper<RiskRule>()
                        .eq(RiskRule::getActionType, actionType)
                        .eq(RiskRule::getStatus, 0)
                        .orderByAsc(RiskRule::getPriority)
                        .orderByAsc(RiskRule::getId)
        );

        String riskLevel = "LOW";
        String result = "PASS";
        Long triggeredRuleId = null;
        String detail = "无风险";
        String ruleName = null;

        // T07：记录先行——占位记录先落库（计数查询包含本次尝试），并发请求在
        // 计数上天然串行化；决策完成后回填最终结论。原"先计数后写记录"在并发下
        // 全部读到阈值前计数，阈值形同虚设。
        RiskRecordDTO pendingRecordDTO = new RiskRecordDTO(
                null, userId, actionType, "LOW", "PENDING", null, "评估中", null, null
        );
        RiskRecordVO pendingRecord = riskRecordService.createRecord(pendingRecordDTO);

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

        // T07：决策回填到占位记录（同一条事实，先占位后定论）
        if (pendingRecord == null || pendingRecord.id() == null) {
            // 防御：占位记录缺失（不应发生）——决策仍返回但事实链断裂需排查
            log.error("[T07] 占位风控记录缺失，决策未落库, userId={}, actionType={}", userId, actionType);
            RiskCheckResponse response = new RiskCheckResponse(userId, actionType, riskLevel, result, triggeredRuleId, detail);
            RiskCheckVO vo = riskConverter.toRiskCheckVO(response);
            return new RiskCheckVO(vo.passed(), vo.riskLevel(), vo.reason(), ruleName);
        }
        RiskRecord decisionUpdate = new RiskRecord();
        decisionUpdate.setId(pendingRecord.id());
        decisionUpdate.setRiskLevel(riskLevel);
        decisionUpdate.setResult(result);
        decisionUpdate.setRuleId(triggeredRuleId);
        decisionUpdate.setDetail(detail);
        riskRecordMapper.updateById(decisionUpdate);

        RiskCheckResponse response = new RiskCheckResponse(userId, actionType, riskLevel, result, triggeredRuleId, detail);
        RiskCheckVO vo = riskConverter.toRiskCheckVO(response);
        return new RiskCheckVO(vo.passed(), vo.riskLevel(), vo.reason(), ruleName);
    }
}
