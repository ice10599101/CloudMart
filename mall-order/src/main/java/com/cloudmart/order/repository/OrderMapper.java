package com.cloudmart.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * REVIEW-01：用户已完成且包含指定 SKU 的订单（评价资格的权威判定，
     * 订单归属/完成状态/SKU 匹配在同一条 SQL 内核验，防止伪造组合）。
     */
    @Select("SELECT DISTINCT o.id FROM orders o "
            + "JOIN order_items oi ON oi.order_id = o.id "
            + "WHERE o.user_id = #{userId} AND o.status = 'COMPLETED' AND oi.sku_id = #{skuId} "
            + "ORDER BY o.id DESC LIMIT 50")
    List<Long> findCompletedOrderIdsWithSku(@Param("userId") Long userId,
                                            @Param("skuId") Long skuId);

    /** WMS-01 余量：自动收货——SHIPPED 且发货超 N 天的订单 ID（分批处理用） */
    @Select("SELECT id FROM orders WHERE status = 'SHIPPED' "
            + "AND shipped_at <= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "ORDER BY id LIMIT #{limit}")
    List<Long> findAutoConfirmableOrderIds(@Param("days") int days,
                                           @Param("limit") int limit);

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
