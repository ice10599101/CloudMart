package com.cloudmart.wms.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wms.entity.InboundOrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboundOrderItemMapper extends BaseMapper<InboundOrderItem> {

    /** T19：收货累加 CAS——received + delta ≤ expected（防超收）且基于当前值（防并发丢数） */
    @org.apache.ibatis.annotations.Update("UPDATE inbound_order_items "
            + "SET received_quantity = received_quantity + #{delta}, updated_at = NOW(3) "
            + "WHERE id = #{itemId} AND received_quantity + #{delta} <= expected_quantity")
    int receiveIncrement(@org.apache.ibatis.annotations.Param("itemId") Long itemId,
                         @org.apache.ibatis.annotations.Param("delta") Integer delta);
}
