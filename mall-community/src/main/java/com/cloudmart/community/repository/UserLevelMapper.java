package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.cloudmart.community.entity.UserLevel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLevelMapper extends BaseMapper<UserLevel> {

    /** C05：原子经验增量（读改写并发丢更新缺陷修复）；返回影响行数 */
    @org.apache.ibatis.annotations.Update("UPDATE user_levels SET "
            + "exp = exp + #{exp}, total_exp = total_exp + #{exp}, updated_at = NOW() "
            + "WHERE user_id = #{userId}")
    int incrementExp(@Param("userId") Long userId, @Param("exp") int exp);

    /** C05：等级条件推进（重算后仅在实际升级时调用） */
    @org.apache.ibatis.annotations.Update("UPDATE user_levels SET level = #{newLevel}, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND level < #{newLevel}")
    int advanceLevel(@Param("userId") Long userId, @Param("newLevel") int newLevel);
}
