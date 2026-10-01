package com.cloudmart.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String orderNo;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private BigDecimal discountAmount;

    private Long couponId;

    private Long activityId;

    private String status;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private LocalDateTime shippedAt;

    private String refundReason;

    /** 退款前履约状态（T02：PAID/SHIPPED；拒绝退款时恢复，QA08） */
    private String beforeRefundStatus;

    /** 报价 ID（T03：一报价一单，快照金额以报价为准；uk 兜底） */
    private Long quoteId;

    /** 幂等键（T03：用户域唯一；报价下单=quote-{id}；DB 权威，替代 Redis 先占键） */
    private String requestKey;

    /** 规范化请求摘要（T03：同键异参 409） */
    private String payloadHash;

    private String refundRejectReason;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
