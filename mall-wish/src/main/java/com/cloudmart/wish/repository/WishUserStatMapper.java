package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.WishUserStat;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WishUserStatMapper extends BaseMapper<WishUserStat> {
}
