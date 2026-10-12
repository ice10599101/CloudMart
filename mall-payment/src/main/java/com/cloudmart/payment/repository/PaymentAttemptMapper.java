package com.cloudmart.payment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.payment.entity.PaymentAttempt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentAttemptMapper extends BaseMapper<PaymentAttempt> {

    /**
     * R09 新建支付尝试（普通插入）。并发安全由两层保证：
     * 服务层先取同订单 MySQL 命名锁串行化；uk_attempt_active_order 唯一索引
     * （active_order_id 生成列）作为数据库硬约束兜底，最多一笔 PENDING/订单。
     */
    @Insert("INSERT INTO payment_attempt (order_id, merchant_payment_no, channel, scene, request_key, "
            + "amount, currency, status) "
            + "VALUES (#{orderId}, #{merchantPaymentNo}, #{channel}, #{scene}, #{requestKey}, "
            + "#{amount}, #{currency}, 'PENDING')")
    int insertAttempt(@Param("orderId") Long orderId,
                      @Param("merchantPaymentNo") String merchantPaymentNo,
                      @Param("channel") String channel,
                      @Param("scene") String scene,
                      @Param("requestKey") String requestKey,
                      @Param("amount") java.math.BigDecimal amount,
                      @Param("currency") String currency);

    /** R09 幂等键反查（同键稳定返回同一笔尝试） */
    @Select("SELECT * FROM payment_attempt WHERE request_key = #{requestKey} LIMIT 1")
    PaymentAttempt findByRequestKey(@Param("requestKey") String requestKey);

    /**
     * R09 同订单创建串行化：取该订单最新尝试行的行锁/间隙锁（idx_attempt_order），
     * 配合命名锁使用；无历史行时返回 null（命名锁仍覆盖首笔并发）。
     */
    @Select("SELECT * FROM payment_attempt WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1 FOR UPDATE")
    PaymentAttempt selectLatestByOrderForUpdate(@Param("orderId") Long orderId);

    /** R09 换渠道：CAS 关闭指定的活动尝试（仅仍为 PENDING 时生效） */
    @Update("UPDATE payment_attempt SET status = 'CLOSED', version = version + 1 "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int closeActiveById(@Param("id") Long id);

    /** R09 同订单命名锁（连接级；事务内获取，finally 释放，超时返回 0） */
    @Select("SELECT GET_LOCK(CONCAT('payment_attempt_order_', #{orderId}), #{timeoutSeconds})")
    Integer acquireOrderLock(@Param("orderId") Long orderId,
                             @Param("timeoutSeconds") int timeoutSeconds);

    /** R09 释放同订单命名锁 */
    @Select("SELECT RELEASE_LOCK(CONCAT('payment_attempt_order_', #{orderId}))")
    Integer releaseOrderLock(@Param("orderId") Long orderId);

    /** PAY-01：CAS PENDING → SUCCESS（金额一致 + 渠道交易号落库），幂等一次性 */
    @Update("UPDATE payment_attempt SET status = 'SUCCESS', provider_txn_no = #{providerTxnNo}, "
            + "version = version + 1 "
            + "WHERE merchant_payment_no = #{merchantPaymentNo} AND status = 'PENDING' "
            + "AND amount = #{amount}")
    int confirmSuccess(@Param("merchantPaymentNo") String merchantPaymentNo,
                       @Param("amount") java.math.BigDecimal amount,
                       @Param("providerTxnNo") String providerTxnNo);

    @Select("SELECT * FROM payment_attempt WHERE merchant_payment_no = #{merchantPaymentNo}")
    PaymentAttempt findByMerchantPaymentNo(@Param("merchantPaymentNo") String merchantPaymentNo);

    /** T02：按主键加锁读——退款核算"已退+在途"前锁定原支付行，串行化并发退款 */
    @Select("SELECT * FROM payment_attempt WHERE id = #{id} FOR UPDATE")
    PaymentAttempt selectByIdForUpdate(@Param("id") Long id);

    /** T02：按订单锁定最近一笔成功支付（订单侧不持有 attemptId 时由本服务权威解析） */
    @Select("SELECT * FROM payment_attempt WHERE order_id = #{orderId} AND status = 'SUCCESS' "
            + "ORDER BY id DESC LIMIT 1 FOR UPDATE")
    PaymentAttempt selectLatestSuccessByOrderForUpdate(@Param("orderId") Long orderId);
}
