package com.cloudmart.risk.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.risk.entity.BlacklistEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlacklistEntryRepository extends BaseMapper<BlacklistEntry> {
}
