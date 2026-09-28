package com.cloudmart.payment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.payment.entity.PaymentNotifyLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentNotifyLogMapper extends BaseMapper<PaymentNotifyLog> {

    /** PAY-01：通知登记唯一（channel + notification_id）——0 行表示重放通知 */
    @Insert("INSERT IGNORE INTO payment_notify_log (channel, notification_id, signature_valid, handle_result, detail) "
            + "VALUES (#{channel}, #{notificationId}, #{signatureValid}, #{handleResult}, #{detail})")
    int record(@Param("channel") String channel,
               @Param("notificationId") String notificationId,
               @Param("signatureValid") boolean signatureValid,
               @Param("handleResult") String handleResult,
               @Param("detail") String detail);
}
