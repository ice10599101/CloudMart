package com.cloudmart.coupon.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 优惠券兑换码实体
 * <p>
 * 用于"指定发放"模式下的优惠券兑换码管理。每个兑换码关联一个优惠券模板，
 * 用户凭码兑换后即可领取对应优惠券。序列号字段配合 Redis BitMap 实现防重兑。
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("exchange_codes")
public class ExchangeCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 兑换码(Base32编码, 去除易混淆字符) */
    private String code;

    /** 关联的优惠券模板ID */
    private Long templateId;

    /** 序列号(用于BitMap位定位) */
    private Integer serialNumber;

    /** 状态: UNUSED-未兑换, EXCHANGED-已兑换, DISABLED-已作废 */
    private String status;

    /** 兑换用户ID */
    private Long userId;

    /** 生成批次号(用于批量管理) */
    private String exchangeBatch;

    /** 兑换时间 */
    private LocalDateTime exchangedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
