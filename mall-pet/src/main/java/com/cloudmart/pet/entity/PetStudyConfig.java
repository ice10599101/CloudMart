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
 * 读书课程配置。读书主要提升智力与经验（原文档 §12）；
 * 智力后续影响学习速度/工作收益/战斗，形成玩法内循环。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_study_config")
public class PetStudyConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 课程名（如：编程入门） */
    private String name;

    private String description;

    /** 分类：文学/历史/科学/艺术/编程/社交/心理/冒险 */
    private String category;

    /** 学习时长（秒） */
    private Integer durationSeconds;

    /** 消耗精力 */
    private Integer energyCost;

    /** 宠物经验奖励 */
    private Integer expReward;

    /** 智力提升 */
    private Integer intelligenceReward;

    /** 接课最低等级 */
    private Integer requiredLevel;

    private Boolean enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
