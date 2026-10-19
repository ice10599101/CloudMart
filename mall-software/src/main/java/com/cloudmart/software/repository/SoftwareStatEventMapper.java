package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareStatEvent;
import org.apache.ibatis.annotations.Mapper;

/** 收藏/订阅事实事件 Mapper（SF06） */
@Mapper
public interface SoftwareStatEventMapper extends BaseMapper<SoftwareStatEvent> {
}
