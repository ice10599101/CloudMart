package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetUserSanction;
import org.apache.ibatis.annotations.Mapper;

/** PetUserSanctionMapper：MyBatis-Plus BaseMapper（R05 处罚事实）。 */
@Mapper
public interface PetUserSanctionMapper extends BaseMapper<PetUserSanction> {
}
