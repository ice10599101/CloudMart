package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareErasureTask;

/** 软件域注销任务 Mapper（唯一键 user_id 幂等首插） */
public interface SoftwareErasureTaskMapper extends BaseMapper<SoftwareErasureTask> {
}
