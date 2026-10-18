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
 * 通行证逐档领取事实（R05 权威）：uk(season,user,tier)——并发领不同档互不覆盖、
 * 同档只交付一次；CLAIM_PENDING → SETTLED，交付失败可恢复补发。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_season_pass_claim")
public class PetSeasonPassClaim {

    public static final String STATUS_CLAIM_PENDING = "CLAIM_PENDING";
    public static final String STATUS_SETTLED = "SETTLED";
    /** T18：交付重试达上限或不可重试错误 → 人工处置（可见可处置，不再占补发头部） */
    public static final String STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seasonId;

    private Long userId;

    private Integer tier;

    private String status;

    /** T18：交付尝试次数（失败累计） */
    private Integer attempts;

    /** T18：下次重试时间（UTC；NULL=立即可执行） */
    private LocalDateTime nextRetryAt;

    /** T18：最近失败错误码 */
    private String lastErrorCode;

    /** T18：交付租约持有者（实例 ID；NULL=空闲） */
    private String leaseOwner;

    /** T18：租约到期时间（UTC） */
    private LocalDateTime leaseUntil;

    /** T18：最近一次尝试时间（UTC） */
    private LocalDateTime lastAttemptAt;

    private String rewardSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime settledAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
