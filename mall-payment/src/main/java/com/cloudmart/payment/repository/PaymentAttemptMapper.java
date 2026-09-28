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

    /** PAY-01：单订单单活动尝试——存在 PENDING 活动尝试时拒绝新建（条件插入返回 0 行） */
    @Insert("INSERT INTO payment_attempt (order_id, merchant_payment_no, channel, amount, currency, status) "
            + "SELECT #{orderId}, #{merchantPaymentNo}, #{channel}, #{amount}, #{currency}, 'PENDING' "
            + "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM payment_attempt "
            + "WHERE order_id = #{orderId} AND status = 'PENDING')")
    int insertSingleActive(@Param("orderId") Long orderId,
                           @Param("merchantPaymentNo") String merchantPaymentNo,
                           @Param("channel") String channel,
                           @Param("amount") java.math.BigDecimal amount,
                           @Param("currency") String currency);

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
}
