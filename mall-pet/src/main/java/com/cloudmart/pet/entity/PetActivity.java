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
 * 统一活动记录（打工/读书/捞瓶/休息共用一套状态机）。
 *
 * <p>并发约束：uk_activity_user_active 函数唯一索引保证每用户每类型至多一条
 * IN_PROGRESS 记录（开工撞车数据库层兜底）；领取用条件 UPDATE CAS 幂等。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_activity")
public class PetActivity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long petId;

    private Long userId;

    /** WORK/STUDY/BOTTLE_FISHING/REST */
    private String activityType;

    /** 关联配置 ID（WORK→pet_job_config.id；STUDY→pet_study_config.id；捞瓶/休息为空） */
    private Long configId;

    /** IN_PROGRESS/COMPLETED/CLAIMED/EXPIRED */
    private String status;

    private LocalDateTime startedAt;

    /** 预计完成时间（started_at + duration，懒判定完成的依据） */
    private LocalDateTime finishedAt;

    /** 领取时间（幂等标记） */
    private LocalDateTime claimedAt;

    /** 结果 JSON（奖励明细/捞瓶 outcome 与 bottleId） */
    private String result;

    /** 规则快照 JSON（开始时冻结：名称/消耗/时长/基础奖励，B09 完成结算只读快照） */
    private String snapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
