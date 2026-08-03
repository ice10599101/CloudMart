package com.cloudmart.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT MAX(CAST(username AS UNSIGNED)) FROM users WHERE deleted_at IS NULL")
    Long selectMaxXiaoDaHao();
}
