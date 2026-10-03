package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetMinigameOperation;
import org.apache.ibatis.annotations.Mapper;

/** PetMinigameOperationMapper：MyBatis-Plus BaseMapper（R11 窗口操作唯一事实）。 */
@Mapper
public interface PetMinigameOperationMapper extends BaseMapper<PetMinigameOperation> {
}
