package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetRelation;
import org.apache.ibatis.annotations.Mapper;

/** 宠物关系 Mapper。 */
@Mapper
public interface PetRelationMapper extends BaseMapper<PetRelation> {
}
