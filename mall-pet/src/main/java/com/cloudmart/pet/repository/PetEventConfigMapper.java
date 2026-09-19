package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetEventConfig;
import org.apache.ibatis.annotations.Mapper;

/** PetEventConfigMapper：MyBatis-Plus BaseMapper（无 XML，LambdaWrapper 查询）。 */
@Mapper
public interface PetEventConfigMapper extends BaseMapper<PetEventConfig> {
}
