package com.cloudmart.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
