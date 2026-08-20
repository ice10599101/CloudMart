package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.WishFulfillment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WishFulfillmentMapper extends BaseMapper<WishFulfillment> {
}
