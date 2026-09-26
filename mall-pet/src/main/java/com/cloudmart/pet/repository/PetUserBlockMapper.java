package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetUserBlock;
import org.apache.ibatis.annotations.Mapper;

/** PetUserBlockMapper：MyBatis-Plus BaseMapper（复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetUserBlockMapper extends BaseMapper<PetUserBlock> {
}
