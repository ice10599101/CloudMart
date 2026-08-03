package com.cloudmart.cart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
