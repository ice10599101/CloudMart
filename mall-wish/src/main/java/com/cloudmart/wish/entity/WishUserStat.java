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
 * 用户心愿统计实体（1:1 with mall_user.sys_user）。
 *
 * <p>单表聚合避免实时 count。{@code starlightBalance} 为冗余字段，
 * 最终事实来源为 {@code wish_resource_log} 流水。</p>
 *
 * <p>等级规则（见文档 6.5）：等级只升不降，{@code highestLevel} 为历史最高判定依据。
 * {@code totalWishes} 和 {@code totalFulfilled} 为历史累计值，永不 -1。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_user_stat")
public class WishUserStat {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String timezone;

    private Byte level;

    private String levelTitle;

    private Byte highestLevel;

    private Integer starlightBalance;

    private Integer totalWishes;

    private Integer activeWishes;

    private Integer totalFulfilled;

    private Integer totalHelped;

    private Integer totalCheckinDays;

    private LocalDateTime lastActiveAt;

    private Integer riskScore;

    private Boolean isRestricted;

    private LocalDateTime restrictedUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
