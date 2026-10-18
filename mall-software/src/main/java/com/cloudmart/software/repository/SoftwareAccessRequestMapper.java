package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareAccessRequest;
import org.apache.ibatis.annotations.Mapper;

/** 受限来源访问申请 Mapper（SF04）；未结唯一由 DB 生成列唯一键保证 */
@Mapper
public interface SoftwareAccessRequestMapper extends BaseMapper<SoftwareAccessRequest> {
}
