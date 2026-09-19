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
 * 宠物进化配置（原文档 §89 宠物进化）：等级门槛 + 星光消耗 → 提升属性并解锁皮肤。
 *
 * <p>进化链按 {@code stageFrom → stageTo} 逐阶推进，属性提升一次性写入宠物主表。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_evolution_config")
public class PetEvolutionConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    /** 起始进化阶段 */
    private Integer stageFrom;

    /** 目标进化阶段 */
    private Integer stageTo;

    private Integer requiredLevel;

    /** 消耗星光 */
    private Integer costStarlight;

    private Integer bonusMaxHp;

    private Integer bonusStrength;

    private Integer bonusIntelligence;

    private Integer bonusAgility;

    private Integer bonusCharm;

    /** 解锁皮肤编码（可空） */
    private String unlockSkinCode;

    private String icon;

    private Boolean enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
