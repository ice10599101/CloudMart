package com.cloudmart.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.order.entity.OrderQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderQuoteMapper extends BaseMapper<OrderQuote> {

    /** TRADE-01：报价消费 CAS——ACTIVE 且未过期才允许置为 CONSUMED，防一报价多单；
     *  与建单同事务：建单失败回滚后报价自动恢复 ACTIVE */
    @Update("UPDATE order_quote SET status = 'CONSUMED', consumed_by = #{orderId} "
            + "WHERE id = #{quoteId} AND status = 'ACTIVE' AND expires_at > NOW(3)")
    int consume(@Param("quoteId") Long quoteId, @Param("orderId") Long orderId);

    /** 建单成功后回填消费订单号（同事务） */
    @Update("UPDATE order_quote SET consumed_by = #{orderId} WHERE id = #{quoteId}")
    int updateConsumedBy(@Param("quoteId") Long quoteId, @Param("orderId") Long orderId);
}
