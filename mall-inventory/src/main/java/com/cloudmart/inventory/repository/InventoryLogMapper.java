package com.cloudmart.inventory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.inventory.entity.InventoryLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLog> {
}
