package com.cloudmart.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.order.entity.AfterSaleCaseEvent;
import org.apache.ibatis.annotations.Mapper;

/** 售后案件时间线 Mapper（T11）。 */
@Mapper
public interface AfterSaleCaseEventMapper extends BaseMapper<AfterSaleCaseEvent> {
}
