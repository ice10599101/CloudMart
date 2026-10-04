package com.cloudmart.wms.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wms.entity.InboundOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {

    /** T19：完成收货 CAS（PENDING/RECEIVING → COMPLETED） */
    @org.apache.ibatis.annotations.Update("UPDATE inbound_orders SET status = 'COMPLETED', "
            + "completed_time = NOW(3) WHERE id = #{inboundOrderId} AND status IN ('PENDING', 'RECEIVING')")
    int markCompleted(@org.apache.ibatis.annotations.Param("inboundOrderId") Long inboundOrderId);
}
