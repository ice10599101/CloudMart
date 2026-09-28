package com.cloudmart.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 渠道通知日志（PAY-01）：(channel, notification_id) 唯一防重放。 */
@Data
@TableName("payment_notify_log")
public class PaymentNotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channel;

    private String notificationId;

    private Boolean signatureValid;

    /** SUCCESS / DUPLICATE / REJECTED / FAILED */
    private String handleResult;

    private Long paymentAttemptId;

    private String detail;

    private LocalDateTime createdAt;
}
