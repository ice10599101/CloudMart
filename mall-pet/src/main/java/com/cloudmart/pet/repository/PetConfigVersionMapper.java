package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetConfigVersion;
import org.apache.ibatis.annotations.Mapper;

/** PetConfigVersionMapper：MyBatis-Plus BaseMapper（复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetConfigVersionMapper extends BaseMapper<PetConfigVersion> {
}
