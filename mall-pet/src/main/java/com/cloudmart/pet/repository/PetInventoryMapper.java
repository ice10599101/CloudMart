package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetInventory;
import org.apache.ibatis.annotations.Mapper;

/** PetInventoryMapper：MyBatis-Plus BaseMapper（无 XML，LambdaWrapper 查询）。 */
@Mapper
public interface PetInventoryMapper extends BaseMapper<PetInventory> {
}
