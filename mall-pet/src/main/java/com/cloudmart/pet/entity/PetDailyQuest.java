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
 * 宠物每日任务进度（{@code uk_pet_daily_quest}：pet × 日期 × 任务 唯一）。
 *
 * <p>进度由业务埋点累加（{@code progress = LEAST(progress + n, target)}），
 * 领奖走 CAS（{@code status='CLAIMED' WHERE id=? AND status='COMPLETE'}）幂等；
 * {@code targetValue} 生成时快照，后台改配置不影响当日已生成的任务（AGENTS §17）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_daily_quest")
public class PetDailyQuest {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 宠物 ID */
    private Long petId;

    /** 用户 ID（查询冗余） */
    private Long userId;

    /** 任务日期（UTC 自然日） */
    private LocalDate questDate;

    /** 任务编码（pet_daily_quest_config.code） */
    private String questCode;

    /** 当前进度 */
    private Integer progress;

    /** 目标值（生成时快照） */
    private Integer targetValue;

    /** 状态：IN_PROGRESS/COMPLETE/CLAIMED */
    private String status;

    /** 完成时间（UTC） */
    private LocalDateTime completedAt;

    /** 领奖时间（UTC，幂等标记） */
    private LocalDateTime claimedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
