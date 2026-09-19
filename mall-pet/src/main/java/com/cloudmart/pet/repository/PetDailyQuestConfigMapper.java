package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetDailyQuestConfig;
import org.apache.ibatis.annotations.Mapper;

/** 每日任务配置 Mapper。 */
@Mapper
public interface PetDailyQuestConfigMapper extends BaseMapper<PetDailyQuestConfig> {
}
