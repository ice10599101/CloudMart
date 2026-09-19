package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetCareerConfig;
import org.apache.ibatis.annotations.Mapper;

/** 宠物职业配置 Mapper（无 XML，LambdaWrapper 查询）。 */
@Mapper
public interface PetCareerConfigMapper extends BaseMapper<PetCareerConfig> {
}
