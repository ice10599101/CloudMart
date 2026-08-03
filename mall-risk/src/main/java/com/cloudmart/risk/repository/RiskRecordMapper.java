package com.cloudmart.risk.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.risk.entity.RiskRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskRecordMapper extends BaseMapper<RiskRecord> {
}
