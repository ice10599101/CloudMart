package com.cloudmart.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.admin.entity.AdminPost;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminPostMapper extends BaseMapper<AdminPost> {
}
