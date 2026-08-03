package com.cloudmart.risk.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.risk.entity.RiskRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskRuleMapper extends BaseMapper<RiskRule> {
}
