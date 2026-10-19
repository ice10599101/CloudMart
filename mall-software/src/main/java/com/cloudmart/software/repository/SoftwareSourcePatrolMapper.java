package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareSourcePatrol;
import org.apache.ibatis.annotations.Mapper;

/** 来源健康巡检记录 Mapper（SF07） */
@Mapper
public interface SoftwareSourcePatrolMapper extends BaseMapper<SoftwareSourcePatrol> {
}
