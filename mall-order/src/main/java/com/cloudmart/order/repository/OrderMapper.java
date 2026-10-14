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

    /** 心愿关联商品闭环（§6）：校验订单属于该用户、已完成且包含该商品的条目 */
    @Select("SELECT COUNT(1) FROM orders o "
            + "WHERE o.id = #{orderId} AND o.user_id = #{userId} AND o.status = 'COMPLETED' "
            + "AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = #{productId})")
    int countCompletedOrderWithProduct(@Param("userId") Long userId,
                                       @Param("productId") Long productId,
                                       @Param("orderId") Long orderId);

    /** N-5 问大家：用户是否已完成购买含该商品的订单（回答"已购"徽标快照） */
    @Select("SELECT COUNT(1) FROM orders o "
            + "WHERE o.user_id = #{userId} AND o.status = 'COMPLETED' "
            + "AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = #{productId})")
    int countCompletedOrdersWithProduct(@Param("userId") Long userId,
                                        @Param("productId") Long productId);

    /**
     * WMS-01 余量：自动收货——SHIPPED 且发货超 N 天的订单 ID（分批处理用）。
     * T11：有未结售后案件（PENDING/APPROVED）的订单排除——处理中售后不得被自动完成。
     */
    @Select("SELECT o.id FROM orders o WHERE o.status = 'SHIPPED' "
            + "AND o.shipped_at <= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "AND NOT EXISTS (SELECT 1 FROM after_sale_case c "
            + "  WHERE c.order_id = o.id AND c.status IN ('PENDING','APPROVED')) "
            + "ORDER BY o.id LIMIT #{limit}")
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

    @Update("UPDATE orders SET status = #{targetStatus}, before_refund_status = #{expectedStatus}, refund_reason = #{refundReason}, updated_at = NOW() " +
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

    // T02/QA08：恢复退款前履约状态（PAID 或 SHIPPED），不再一律回 PAID
    @Update("UPDATE orders SET status = before_refund_status, refund_reject_reason = #{rejectReason}, before_refund_status = NULL, updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = #{expectedStatus} AND before_refund_status IS NOT NULL")
    int updateStatusRejectRefund(@Param("orderId") Long orderId,
                                 @Param("expectedStatus") String expectedStatus,
                                 @Param("rejectReason") String rejectReason);

    /** T04：订单行锁——售后额度预占/全额推进以订单行为串行化锚点（锁顺序：订单行→案件行） */
    @Select("SELECT * FROM orders WHERE id = #{orderId} FOR UPDATE")
    Order selectByIdForUpdate(@Param("orderId") Long orderId);

    /** T04：退款汇总回填（已退累计 + NONE/PARTIAL/FULL；调用方持订单行锁） */
    @Update("UPDATE orders SET refunded_amount = #{refundedAmount}, refund_status = #{refundStatus}, updated_at = NOW() " +
            "WHERE id = #{orderId}")
    int updateRefundSummary(@Param("orderId") Long orderId,
                            @Param("refundedAmount") java.math.BigDecimal refundedAmount,
                            @Param("refundStatus") String refundStatus);

    /** T04：全额退款完成推进履约状态（部分退款绝不触发——调用方已按实付核算） */
    @Update("UPDATE orders SET status = 'REFUNDED', updated_at = NOW() " +
            "WHERE id = #{orderId} AND status IN ('PAID', 'SHIPPED', 'REFUNDING')")
    int updateStatusToRefundedForFullRefund(@Param("orderId") Long orderId);
}
