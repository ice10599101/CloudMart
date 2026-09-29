package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetFriendFeedCursor;
import org.apache.ibatis.annotations.Mapper;

/** PetFriendFeedCursorMapper：MyBatis-Plus BaseMapper（复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetFriendFeedCursorMapper extends BaseMapper<PetFriendFeedCursor> {
}
