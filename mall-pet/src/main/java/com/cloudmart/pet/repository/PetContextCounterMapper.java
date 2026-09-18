package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetContextCounter;
import org.apache.ibatis.annotations.Mapper;

/** PetContextCounterMapper：MyBatis-Plus BaseMapper（全项目约定：复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetContextCounterMapper extends BaseMapper<PetContextCounter> {
}
