package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.WishFence;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FenceMapper extends BaseMapper<WishFence> {
}
