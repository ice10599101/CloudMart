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
 * 打工岗位配置（后台可维护；参数硬编码在前端是禁止项，原文档 §11）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_job_config")
public class PetJobConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 岗位名（如：咖啡店兼职） */
    private String name;

    private String description;

    /** 工作时长（秒） */
    private Integer durationSeconds;

    /** 消耗精力 */
    private Integer energyCost;

    /** 消耗饥饿（越工作越饿） */
    private Integer hungerCost;

    /** 宠物经验奖励 */
    private Integer expReward;

    /** 星光奖励（经 mall-wish 内部端点发放，流水来源 PET_REWARD） */
    private Integer currencyReward;

    /** 接单最低等级 */
    private Integer requiredLevel;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
