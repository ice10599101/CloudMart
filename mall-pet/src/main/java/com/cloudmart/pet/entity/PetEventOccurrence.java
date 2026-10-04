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
 * 限时活动期次（R33）：活动按期发布——occurrenceId 隔离历史期次，
 * 奖励快照在发布时冻结；领奖事实唯一 (petId, occurrenceId)，
 * 旧 eventCode 入口仅当能唯一解析当前一期时放行。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_event_occurrence")
public class PetEventOccurrence {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventCode;

    /** 第几期（同 code 递增，从 1 起） */
    private Integer occurrenceIndex;

    /** 统计窗口开始(UTC) */
    private LocalDateTime startAt;

    /** 统计窗口结束(UTC) */
    private LocalDateTime endAt;

    /** 领奖截止(UTC，结束+宽限期) */
    private LocalDateTime claimDeadlineAt;

    /** 奖励快照（发布期次时冻结） */
    private String rewardSnapshot;

    /** ACTIVE / CLOSED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
