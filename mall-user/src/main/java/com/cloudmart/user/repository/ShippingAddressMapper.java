package com.cloudmart.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.user.entity.ShippingAddress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShippingAddressMapper extends BaseMapper<ShippingAddress> {
}
