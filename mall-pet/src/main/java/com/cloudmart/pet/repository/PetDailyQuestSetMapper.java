package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetDailyQuestSet;
import org.apache.ibatis.annotations.Mapper;

/** 每日任务集 Mapper（PET-09：每宠每业务日一个冻结任务集）。 */
@Mapper
public interface PetDailyQuestSetMapper extends BaseMapper<PetDailyQuestSet> {
}
