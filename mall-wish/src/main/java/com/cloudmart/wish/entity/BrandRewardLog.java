package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 品牌奖励发放日志（文档表㉕，Sprint 3.6 达标发奖链路）。
 */
@Getter
@Setter
@TableName("wish_brand_reward_log")
public class BrandRewardLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long poolId;

    private Long userId;

    private String rewardType;

    private Integer rewardAmount;

    private String couponCode;

    private LocalDateTime grantedAt;
}
