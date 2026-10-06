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

    /**
     * PET-07/T10：动作事务的宠物行锁加载（FOR UPDATE；软删列手工过滤）。
     * 动作在持锁后先结算衰减再应用增量，衰减 CAS（绝对值写）与动作相对增量
     * 不再交错，喂食/清洁/经验与自然衰减并发不丢增量。
     */
    @org.apache.ibatis.annotations.Select(
            "SELECT * FROM pet WHERE user_id = #{userId} AND is_active = 1 AND deleted_at IS NULL LIMIT 1 FOR UPDATE")
    Pet selectActiveForUpdate(@org.apache.ibatis.annotations.Param("userId") Long userId);
}
