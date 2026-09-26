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


@Getter
@Setter
@NoArgsConstructor
@TableName("pet_onboarding_progress")
public class PetOnboardingProgress {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 新手引导进度（N01）：每用户每引导版本唯一；领域事件驱动完成。 */
    private Long userId;
    private String guideVersion;
    /** 各步骤状态 JSON: {FEED:DONE, PLAY:DONE, WORK:IN_PROGRESS, DECORATE:LOCKED} */
    private String steps;
    private java.time.LocalDateTime skippedAt;
    private java.time.LocalDateTime completedAt;
    /** 基础家具赠送奖励 operationId（每用户一次，重试不重复入包） */
    private String furnitureGrantOpId;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
