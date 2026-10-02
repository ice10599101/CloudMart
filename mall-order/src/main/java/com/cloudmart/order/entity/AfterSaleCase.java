package com.cloudmart.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后案件（T11）：用户售后的权威事实——原因/类型/数量/附件/受理结论/时间线。
 * 与 T02 refund_order 分工：案件管"是否同意售后"，退款单管"资金怎么退"；
 * 案件批准后凭 refund_no 关联退款单，退款完成事件回填 REFUNDED。
 */
@Data
@TableName("after_sale_case")
public class AfterSaleCase {

    public static final String TYPE_REFUND_ONLY = "REFUND_ONLY";
    public static final String TYPE_RETURN_REFUND = "RETURN_REFUND";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String caseNo;

    private Long orderId;

    private Long userId;

    /** 订单项 ID（null=整单售后） */
    private Long itemId;

    private String type;

    private String reason;

    /** 附件文件 ID JSON 数组（S01 资产 ID，文件资产归属校验复用 mall-file） */
    private String attachmentFileIds;

    /** 售后数量（0=整单全部） */
    private Integer quantity;

    private String status;

    /** 关联退款单号（受理后由运营/系统发起 T02 退款时回填） */
    private String refundNo;

    private BigDecimal refundAmount;

    private String rejectReason;

    private Long handledBy;

    private LocalDateTime handledAt;

    private String returnCarrier;

    private String returnTrackingNo;

    private LocalDateTime returnShippedAt;

    private String inspectResult;

    private String inspectNote;

    private LocalDateTime inspectedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
