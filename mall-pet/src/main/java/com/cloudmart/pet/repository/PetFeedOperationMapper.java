package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetFeedOperation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物品喂食已结算操作台账 Mapper（R02/V79）。
 */
@Mapper
public interface PetFeedOperationMapper extends BaseMapper<PetFeedOperation> {
}
