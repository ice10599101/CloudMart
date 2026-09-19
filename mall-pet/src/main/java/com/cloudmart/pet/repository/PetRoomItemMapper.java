package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetRoomItem;
import org.apache.ibatis.annotations.Mapper;

/** 宠物房间摆放 Mapper。 */
@Mapper
public interface PetRoomItemMapper extends BaseMapper<PetRoomItem> {
}
