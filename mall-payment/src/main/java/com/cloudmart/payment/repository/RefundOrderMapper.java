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

    /**
     * 幂等插入（T03）：uk(refund_no) 冲突抛 DuplicateKeyException 由服务层做同号重放
     * 判定——不再 INSERT IGNORE 吞掉数据错误（NOT NULL 列缺省被静默写 0 的隐患）。
     * attemptId 恒为服务层权威解析值。
     */
    @Insert("INSERT INTO refund_order (refund_no, payment_attempt_id, order_id, refund_amount, "
            + "currency, reason_code, status) VALUES (#{refundNo}, #{attemptId}, #{orderId}, #{amount}, "
            + "#{currency}, #{reasonCode}, 'REQUESTED')")
    int insertRefund(@Param("refundNo") String refundNo,
                     @Param("attemptId") Long attemptId,
                     @Param("orderId") Long orderId,
                     @Param("amount") BigDecimal amount,
                     @Param("currency") String currency,
                     @Param("reasonCode") String reasonCode);

    /**
     * T03 历史数据自愈：旧行 payment_attempt_id 为 0 时回填权威支付尝试（仅命中
     * 缺失/0 行，指向其他 attempt 的异常行不受影响，留给人工核查）。
     */
    @Update("UPDATE refund_order SET payment_attempt_id = #{attemptId} "
            + "WHERE refund_no = #{refundNo} AND (payment_attempt_id IS NULL OR payment_attempt_id = 0)")
    int backfillAttemptId(@Param("refundNo") String refundNo, @Param("attemptId") Long attemptId);

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
