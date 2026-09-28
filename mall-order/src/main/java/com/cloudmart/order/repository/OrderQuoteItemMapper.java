package com.cloudmart.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.order.entity.OrderQuoteItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderQuoteItemMapper extends BaseMapper<OrderQuoteItem> {
}
