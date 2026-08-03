package com.cloudmart.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Update("UPDATE orders SET status = #{targetStatus}, updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = #{expectedStatus}")
    int updateStatusIfMatch(@Param("orderId") Long orderId,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("targetStatus") String targetStatus);

    @Update("UPDATE orders SET status = #{targetStatus}, shipped_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = #{expectedStatus}")
    int updateStatusAndShippedAtIfMatch(@Param("orderId") Long orderId,
                                        @Param("expectedStatus") String expectedStatus,
                                        @Param("targetStatus") String targetStatus);

    @Update("UPDATE orders SET status = #{targetStatus}, completed_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = #{expectedStatus}")
    int updateStatusAndCompletedAtIfMatch(@Param("orderId") Long orderId,
                                          @Param("expectedStatus") String expectedStatus,
                                          @Param("targetStatus") String targetStatus);

    @Update("UPDATE orders SET status = #{targetStatus}, refund_reason = #{refundReason}, updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = #{expectedStatus}")
    int updateStatusToRefunding(@Param("orderId") Long orderId,
                                @Param("expectedStatus") String expectedStatus,
                                @Param("targetStatus") String targetStatus,
                                @Param("refundReason") String refundReason);

    @Update("UPDATE orders SET status = #{targetStatus}, updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = #{expectedStatus}")
    int updateStatusToRefunded(@Param("orderId") Long orderId,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("targetStatus") String targetStatus);

    @Update("UPDATE orders SET status = #{targetStatus}, refund_reject_reason = #{rejectReason}, updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = #{expectedStatus}")
    int updateStatusRejectRefund(@Param("orderId") Long orderId,
                                 @Param("expectedStatus") String expectedStatus,
                                 @Param("targetStatus") String targetStatus,
                                 @Param("rejectReason") String rejectReason);
}
