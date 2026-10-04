package com.cloudmart.wms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 入库收货流水（T19）：每次收货一条事实——receiptId 幂等、数量为正、验收质量
 * 与业务来源分离。库存入账事件以 receiptId 为唯一事实键（重复消费只入账一次）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("inbound_receipt")
public class InboundReceipt {

    public static final String QUALITY_PASSED = "PASSED";
    public static final String QUALITY_QUARANTINE = "QUARANTINE";
    public static final String SOURCE_PURCHASE = "PURCHASE_INBOUND";
    public static final String SOURCE_AFTER_SALE = "AFTER_SALE_RETURN";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String receiptId;

    private Long inboundOrderId;

    private Long inboundItemId;

    private Long skuId;

    private Integer quantity;

    private Long operatorId;

    private String qualityResult;

    private String bizSource;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
