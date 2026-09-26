package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetOperation;
import org.apache.ibatis.annotations.Mapper;

/** PetOperationMapper：MyBatis-Plus BaseMapper（全项目约定：复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetOperationMapper extends BaseMapper<PetOperation> {
}
