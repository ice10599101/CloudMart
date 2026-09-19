package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetRoom;
import org.apache.ibatis.annotations.Mapper;

/** 宠物房间 Mapper。 */
@Mapper
public interface PetRoomMapper extends BaseMapper<PetRoom> {
}
