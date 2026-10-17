package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.WishUserStat;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WishUserStatMapper extends BaseMapper<WishUserStat> {

    /** OPS-02：用户行锁（FOR UPDATE）——导出任务创建路径串行化，消除并发竞态窗口 */
    @org.apache.ibatis.annotations.Select("SELECT id FROM wish_user_stat WHERE user_id = #{userId} FOR UPDATE")
    Long forUpdateUser(@org.apache.ibatis.annotations.Param("userId") Long userId);
}