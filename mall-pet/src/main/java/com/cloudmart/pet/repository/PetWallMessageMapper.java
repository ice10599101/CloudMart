package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetWallMessage;
import org.apache.ibatis.annotations.Mapper;

/** 宠物留言墙 Mapper。 */
@Mapper
public interface PetWallMessageMapper extends BaseMapper<PetWallMessage> {
}
