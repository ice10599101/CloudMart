package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetFriend;
import org.apache.ibatis.annotations.Mapper;

/** 宠物好友 Mapper。 */
@Mapper
public interface PetFriendMapper extends BaseMapper<PetFriend> {
}
