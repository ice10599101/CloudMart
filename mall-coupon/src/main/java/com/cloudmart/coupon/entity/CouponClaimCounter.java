package com.cloudmart.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 领券计数台账（COUPON-01）：每人每模板一行，原子递增作为限领权威判定。 */
@Data
@TableName("coupon_claim_counter")
public class CouponClaimCounter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long templateId;

    private Integer claimedCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
