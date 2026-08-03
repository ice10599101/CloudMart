package com.cloudmart.wms.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wms.entity.ShippingOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShippingOrderMapper extends BaseMapper<ShippingOrder> {
}
