package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.Pet;
import org.apache.ibatis.annotations.Mapper;

/** PetMapper：MyBatis-Plus BaseMapper（全项目约定：复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetMapper extends BaseMapper<Pet> {

    /** P04：候选池主键上界（随机起点采样用；空表返回 null） */
    @org.apache.ibatis.annotations.Select("SELECT MAX(id) FROM pet")
    Long selectMaxId();
}
