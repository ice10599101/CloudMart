package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwarePublisherApplication;
import org.apache.ibatis.annotations.Mapper;

/** 发布者申请 Mapper（SF03）；未结唯一由 DB 生成列唯一键保证 */
@Mapper
public interface SoftwarePublisherApplicationMapper
        extends BaseMapper<SoftwarePublisherApplication> {
}
