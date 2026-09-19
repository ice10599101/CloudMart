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
 * 宠物装备配置（商城在售，原文档 §6.1 宠物背包/装备、§89 装备）。
 *
 * <p>加成数值全部在服务端：前端只展示，装备生效由
 * {@code PetStatsService} 汇总装备加成后参与战斗/捞瓶等玩法。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_equipment_config")
public class PetEquipmentConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一编码 */
    private String code;

    private String name;

    private String description;

    /** 部位：HAT/NECKLACE/SCARF/BACKPACK */
    private String slot;

    /** 图标（emoji 或 URL） */
    private String icon;

    /** 稀有度：COMMON/RARE/EPIC */
    private String rarity;

    /** 售价（星光） */
    private Integer priceStarlight;

    private Integer bonusStrength;

    private Integer bonusIntelligence;

    private Integer bonusAgility;

    private Integer bonusCharm;

    /** 生命上限加成 */
    private Integer bonusMaxHp;

    /** 购买/装备最低等级 */
    private Integer requiredLevel;

    /** 购买/装备最低进化阶段 */
    private Integer requiredEvolutionStage;

    private Boolean enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
