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
 * 宠物皮肤配置（原文档 §89 宠物皮肤/宠物商城）。
 *
 * <p>皮肤只改外观：穿戴时把 {@code color/accessory} 写入 {@code pet.appearance}
 * 并记录 {@code pet.skin_code}；卸载皮肤恢复原生外观（颜色取种类默认色）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_skin_config")
public class PetSkinConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    /** 限定种类（NULL=通用） */
    private String species;

    /** 主色（前端/Cocos 调色板键） */
    private String color;

    /** 配饰键 */
    private String accessory;

    private String icon;

    /** 稀有度：COMMON/RARE/EPIC */
    private String rarity;

    private Integer priceStarlight;

    private Integer requiredLevel;

    private Integer requiredEvolutionStage;

    private Boolean enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
