package com.cloudmart.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.order.entity.OrderQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderQuoteMapper extends BaseMapper<OrderQuote> {

    /** TRADE-01：报价消费 CAS——ACTIVE 且未过期才允许置为 CONSUMED，防一报价多单；
     *  与建单同事务：建单失败回滚后报价自动恢复 ACTIVE。
     *  T20：expires_at 由 Java 以 UTC 写入，过期判断改用 UTC_TIMESTAMP(3) 与之对齐——
     *  原 NOW(3) 取数据库会话时区，服务器/会话非 UTC 时报价 TTL 会偏移数小时。 */
    @Update("UPDATE order_quote SET status = 'CONSUMED', consumed_by = #{orderId} "
            + "WHERE id = #{quoteId} AND status = 'ACTIVE' AND expires_at > UTC_TIMESTAMP(3)")
    int consume(@Param("quoteId") Long quoteId, @Param("orderId") Long orderId);

    /** 建单成功后回填消费订单号（同事务） */
    @Update("UPDATE order_quote SET consumed_by = #{orderId} WHERE id = #{quoteId}")
    int updateConsumedBy(@Param("quoteId") Long quoteId, @Param("orderId") Long orderId);
}
