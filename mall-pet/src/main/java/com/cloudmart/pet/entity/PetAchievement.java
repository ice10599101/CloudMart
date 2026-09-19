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
 * 成就定义（V1 种子 12 枚；condition_type + condition_value 由
 * PetAchievementService 在事件挂载点判定，uk 幂等落 record）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_achievement")
public class PetAchievement {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一编码（如 FIRST_BOTTLE） */
    private String code;

    private String name;

    private String description;

    /** 图标（emoji 或 URL） */
    private String icon;

    /** 判定类型：BOTTLE_COUNT/BATTLE_WIN/LEVEL/CHAT_COUNT/STATS_FULL/ACTIVITY_COUNT */
    private String conditionType;

    /** 计数子类型（ACTIVITY_COUNT 时: WORK/STUDY/FEED/CLEAN） */
    private String conditionSubtype;

    /** 判定阈值 */
    private Integer conditionValue;

    /** 达成奖励经验 */
    private Integer expReward;

    private Boolean enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
