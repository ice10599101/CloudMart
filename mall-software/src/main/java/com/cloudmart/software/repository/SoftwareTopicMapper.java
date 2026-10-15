package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareTopic;

/** 软件专题 Mapper（P1）：UNIQUE(slug)；公开查询只读 PUBLISHED */
public interface SoftwareTopicMapper extends BaseMapper<SoftwareTopic> {
}
