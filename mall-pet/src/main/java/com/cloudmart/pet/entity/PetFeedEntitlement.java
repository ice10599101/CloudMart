package com.cloudmart.pet.entity;

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
 * 实物商品联动·双倍喂食权益（§6/R01/R02）：coupon 兑换码核验通过后发放，
 * 一次喂食最多占用一行（consumeOneForFeed CAS），consumed_by_operation_id 唯一防重。
 *
 * <p>状态机：AVAILABLE → CONSUMED（喂食占用）/ EXPIRED（过期）/ REVOKED（退款撤销）。
 * 一份权益一行：同一核验事实发多份用 unit_sequence 区分（uk source_fact_id+unit_sequence）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_feed_entitlement")
public class PetFeedEntitlement {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String redemptionCode;

    /** coupon 侧用户券 ID（历史审计回链，当前链路为空） */
    private Long userCouponId;

    /** 核验事实：mall-coupon exchange_codes.id */
    private Long sourceFactId;

    /** 同一核验事实的第几份权益（一份一行） */
    private Integer unitSequence;

    /** 来源订单行（订单绑定码扩展，当前 NULL） */
    private Long sourceOrderItemId;

    private String itemCode;

    /** 失效时刻（NULL=不失效，服务端判定） */
    private LocalDateTime expiresAt;

    private String status;

    /** 占用该份的喂食操作键（uk，防重） */
    private String consumedByOperationId;

    private Long consumedPetId;

    private LocalDateTime consumedAt;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
