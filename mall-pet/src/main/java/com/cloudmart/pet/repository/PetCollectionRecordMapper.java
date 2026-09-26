package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetCollectionRecord;
import org.apache.ibatis.annotations.Mapper;

/** PetCollectionRecordMapper：MyBatis-Plus BaseMapper（复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetCollectionRecordMapper extends BaseMapper<PetCollectionRecord> {
}
