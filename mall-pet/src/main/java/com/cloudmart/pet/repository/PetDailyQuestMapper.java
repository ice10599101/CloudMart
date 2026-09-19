package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetDailyQuest;
import org.apache.ibatis.annotations.Mapper;

/** 每日任务进度 Mapper。 */
@Mapper
public interface PetDailyQuestMapper extends BaseMapper<PetDailyQuest> {
}
