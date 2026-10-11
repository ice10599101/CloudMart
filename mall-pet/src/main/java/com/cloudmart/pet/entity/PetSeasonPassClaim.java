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

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seasonId;

    private Long userId;

    private Integer tier;

    private String status;

    private String rewardSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime settledAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
