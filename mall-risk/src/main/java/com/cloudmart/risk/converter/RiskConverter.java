package com.cloudmart.risk.converter;

import com.cloudmart.risk.dto.RiskCheckResponse;
import com.cloudmart.risk.dto.RiskRecordDTO;
import com.cloudmart.risk.dto.RiskRuleDTO;
import com.cloudmart.risk.entity.RiskRecord;
import com.cloudmart.risk.entity.RiskRule;
import com.cloudmart.risk.vo.RiskCheckVO;
import com.cloudmart.risk.vo.RiskRecordVO;
import com.cloudmart.risk.vo.RiskRuleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RiskConverter {

    @Mapping(target = "action", source = "actionType")
    @Mapping(target = "reason", source = "detail")
    RiskRecordVO toRiskRecordVO(RiskRecord entity);

    List<RiskRecordVO> toRiskRecordVOList(List<RiskRecord> entities);

    @Mapping(target = "type", source = "actionType")
    @Mapping(target = "action", source = "riskLevel")
    @Mapping(target = "enabled", expression = "java(entity.getStatus() != null && entity.getStatus() == 1)")
    RiskRuleVO toRiskRuleVO(RiskRule entity);

    List<RiskRuleVO> toRiskRuleVOList(List<RiskRule> entities);

    @Mapping(target = "passed", expression = "java(\"PASS\".equals(response.result()))")
    @Mapping(target = "riskLevel", source = "riskLevel")
    @Mapping(target = "reason", source = "detail")
    @Mapping(target = "ruleName", ignore = true)
    RiskCheckVO toRiskCheckVO(RiskCheckResponse response);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RiskRecord toEntity(RiskRecordDTO dto);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RiskRule toEntity(RiskRuleDTO dto);
}
