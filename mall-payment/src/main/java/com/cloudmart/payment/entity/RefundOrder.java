package com.cloudmart.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单（T02）：渠道退款事实台账，与订单审批状态分离。
 *
 * <p>状态机（12.5）：REQUESTED → PROCESSING → SUCCEEDED / FAILED；结果未知 → UNKNOWN
 * （查单收敛，禁止重新生成退款号）。审批只推进到 PROCESSING，渠道确认才 SUCCEEDED；
 * 退款成功不回改支付尝试的 SUCCESS 原事实。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("refund_order")
public class RefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户退款号（调用方提供，幂等键） */
    private String refundNo;

    /** 原成功支付尝试 ID */
    private Long paymentAttemptId;

    /** 订单 ID */
    private Long orderId;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 币种 */
    private String currency;

    /** 退款原因码 */
    private String reasonCode;

    /** REQUESTED / PROCESSING / UNKNOWN / SUCCEEDED / FAILED */
    private String status;

    /** 渠道退款单号（渠道确认后回填） */
    private String providerRefundNo;

    /** 失败/未知错误码 */
    private String errorCode;

    /** 乐观版本 */
    private Integer version;

    /** 下次查单时间（UNKNOWN/PROCESSING 收敛） */
    private LocalDateTime nextQueryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
