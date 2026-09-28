package com.cloudmart.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付尝试台账（PAY-01）：每渠道尝试有稳定商户支付号，CAS PENDING → SUCCESS。 */
@Data
@TableName("payment_attempt")
public class PaymentAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String merchantPaymentNo;

    private String channel;

    private BigDecimal amount;

    private String currency;

    /** PENDING / SUCCESS / CLOSED / FAILED */
    private String status;

    private String providerTxnNo;

    private Integer version;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
