package com.cloudmart.payment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.payment.entity.RefundOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 退款单 Mapper（T02）：状态迁移全部为条件更新——PROCESSING → SUCCEEDED / FAILED /
 * UNKNOWN 只能成功一次；插入以 refund_no 唯一键幂等（同号同参重放返回原结果）。
 */
@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    /** 幂等插入：uk(refund_no) 冲突返回 0 行 */
    @Insert("INSERT IGNORE INTO refund_order (refund_no, payment_attempt_id, order_id, refund_amount, "
            + "currency, reason_code, status) VALUES (#{refundNo}, #{attemptId}, #{orderId}, #{amount}, "
            + "#{currency}, #{reasonCode}, 'REQUESTED')")
    int insertRefund(@Param("refundNo") String refundNo,
                     @Param("attemptId") Long attemptId,
                     @Param("orderId") Long orderId,
                     @Param("amount") BigDecimal amount,
                     @Param("currency") String currency,
                     @Param("reasonCode") String reasonCode);

    /** REQUESTED → PROCESSING（渠道提交）：0 行 = 已提交/已终态 */
    @Update("UPDATE refund_order SET status = 'PROCESSING', version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status = 'REQUESTED'")
    int markProcessing(@Param("refundNo") String refundNo);

    /** PROCESSING/UNKNOWN → SUCCEEDED（渠道确认，一次性） */
    @Update("UPDATE refund_order SET status = 'SUCCEEDED', provider_refund_no = #{providerRefundNo}, "
            + "next_query_at = NULL, version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status IN ('PROCESSING', 'UNKNOWN')")
    int markSucceeded(@Param("refundNo") String refundNo,
                      @Param("providerRefundNo") String providerRefundNo);

    /** PROCESSING → UNKNOWN（渠道结果未知，等待查单） */
    @Update("UPDATE refund_order SET status = 'UNKNOWN', next_query_at = #{nextQueryAt}, "
            + "error_code = #{errorCode}, version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status = 'PROCESSING'")
    int markUnknown(@Param("refundNo") String refundNo,
                    @Param("nextQueryAt") java.time.LocalDateTime nextQueryAt,
                    @Param("errorCode") String errorCode);

    /** PROCESSING/UNKNOWN → FAILED（渠道明确失败，一次性） */
    @Update("UPDATE refund_order SET status = 'FAILED', error_code = #{errorCode}, "
            + "next_query_at = NULL, version = version + 1 "
            + "WHERE refund_no = #{refundNo} AND status IN ('PROCESSING', 'UNKNOWN')")
    int markFailed(@Param("refundNo") String refundNo,
                   @Param("errorCode") String errorCode);

    /** T11：退款层对账扫描——某时间后创建的退款单，id 游标全量（对账分批用） */
    @org.apache.ibatis.annotations.Select("SELECT * FROM refund_order "
            + "WHERE created_at >= #{since} AND id > #{lastId} "
            + "ORDER BY id LIMIT #{limit}")
    java.util.List<RefundOrder> scanForReconciliation(@org.apache.ibatis.annotations.Param("since") java.time.LocalDateTime since,
                                                      @org.apache.ibatis.annotations.Param("lastId") long lastId,
                                                      @org.apache.ibatis.annotations.Param("limit") int limit);

    @Select("SELECT * FROM refund_order WHERE refund_no = #{refundNo}")
    RefundOrder findByRefundNo(@Param("refundNo") String refundNo);

    /** 已成功 + 处理中的退款总额（禁止超退的核算口径，T02） */
    @Select("SELECT COALESCE(SUM(refund_amount), 0) FROM refund_order "
            + "WHERE payment_attempt_id = #{attemptId} AND status IN ('REQUESTED', 'PROCESSING', 'UNKNOWN', 'SUCCEEDED')")
    BigDecimal sumActiveRefundAmount(@Param("attemptId") Long attemptId);
}
