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
 * 实物商品联动·双倍喂食权益（§6）：coupon 兑换码核销后发放，
 * 下一次 feedItem 效果 x2（consume 后标记 used）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_feed_entitlement")
public class PetFeedEntitlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String redemptionCode;

    private Long userCouponId;

    private String itemCode;

    private Boolean used;

    private LocalDateTime usedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
