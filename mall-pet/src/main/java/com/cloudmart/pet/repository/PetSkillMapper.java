package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetSkill;
import org.apache.ibatis.annotations.Mapper;

/** PetSkillMapper：MyBatis-Plus BaseMapper（无 XML，LambdaWrapper 查询）。 */
@Mapper
public interface PetSkillMapper extends BaseMapper<PetSkill> {
}
