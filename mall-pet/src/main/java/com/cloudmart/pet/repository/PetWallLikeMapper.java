package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetWallLike;
import org.apache.ibatis.annotations.Mapper;

/** 宠物留言点赞 Mapper。 */
@Mapper
public interface PetWallLikeMapper extends BaseMapper<PetWallLike> {
}
