package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetDashboardDailySnapshot;
import org.apache.ibatis.annotations.Mapper;

/** PetDashboardDailySnapshotMapper：MyBatis-Plus BaseMapper（复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetDashboardDailySnapshotMapper extends BaseMapper<PetDashboardDailySnapshot> {
}
