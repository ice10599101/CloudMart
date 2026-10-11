package com.cloudmart.wish.entity;

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
 * 跨服务奖励交付义务（R07）：源事实与义务同事务落库（uk biz_key——每个业务事实
 * 每个受益人至多一次），事务提交后即送/失败退避补发；不以日志充当待办。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_reward_obligation")
public class WishRewardObligation {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String TYPE_EXP = "EXP";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizKey;

    private String source;

    private String sourceBizId;

    private Long beneficiary;

    private String rewardType;

    private Integer amount;

    private String description;

    private String status;

    private Integer attempts;

    private String lastError;

    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime settledAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
