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

    /** WISH-03：结算状态机 PENDING（义务已登记）/CREDITED（已入账）/FAILED */
    private String status;

    /** WISH-03：承诺数量（登记义务时冻结） */
    private Integer promisedAmount;

    /** WISH-03：实际入账数量（每日上限时可少于承诺——不记录"全额已发"） */
    private Integer creditedAmount;

    private Integer retryCount;

    private String lastError;

    /** WISH-03：幂等入账操作键（earnStarlightIdempotent operationId） */
    private String operationId;

    private String couponCode;

    private LocalDateTime grantedAt;
}
