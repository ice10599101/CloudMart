package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 宠物技能配置（原文档 §12 智力/技能联动、§89 宠物技能）。
 *
 * <p>{@code effect} 是服务端公式的唯一开关（枚举 {@code PetSkillEffect}）；
 * {@code effectValue} 语义随 effect 而定：百分比为 0-1 小数、点数为整数。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_skill_config")
public class PetSkillConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    /** 类型：ACTIVE/PASSIVE */
    private String skillType;

    /** 效果标识：POWER_STRIKE/LUCKY_FISH/QUICK_STEP/BOOKWORM/CHARM_AURA/TOUGH_BODY */
    private String effect;

    /** 效果数值（百分比 0-1 小数 / 点数整数） */
    private BigDecimal effectValue;

    private String icon;

    /** 售价（星光，购买后落背包技能书） */
    private Integer priceStarlight;

    private Integer requiredLevel;

    private Boolean enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
