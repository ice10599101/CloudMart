package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetStudyConfig;
import org.apache.ibatis.annotations.Mapper;

/** PetStudyConfigMapper：MyBatis-Plus BaseMapper（全项目约定：复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetStudyConfigMapper extends BaseMapper<PetStudyConfig> {
}
