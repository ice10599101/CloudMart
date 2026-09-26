package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetAlbumAsset;
import org.apache.ibatis.annotations.Mapper;

/** PetAlbumAssetMapper：MyBatis-Plus BaseMapper（复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetAlbumAssetMapper extends BaseMapper<PetAlbumAsset> {
}
