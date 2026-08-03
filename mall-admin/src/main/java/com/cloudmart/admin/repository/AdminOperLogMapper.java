package com.cloudmart.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.admin.entity.AdminOperLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminOperLogMapper extends BaseMapper<AdminOperLog> {
}
