package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 陪伴按宠物日分账（R36）：用户日总额（pet_companion_daily）不变，
 * 本表提供按宠物可审计明细——pet.companionSeconds 只累计本宠实际 accepted，
 * 不复制用户总额；qualifiedDay 达到阈值才计有效陪伴日/连续天数。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_companion_daily_pet")
public class PetCompanionDailyPet {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long petId;

    private LocalDate businessDate;

    /** 本宠当日实际计入有效秒数（受账号日上限截断） */
    private Integer acceptedSeconds;

    /** 本宠当日已产生的陪伴任务分钟数（floor 差值口径） */
    private Integer grantedMinutes;

    /** 是否有效陪伴日（达到 qualifiedDayThresholdSeconds） */
    private Integer qualifiedDay;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
