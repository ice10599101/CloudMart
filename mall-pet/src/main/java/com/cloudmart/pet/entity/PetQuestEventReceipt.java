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
 * 任务事件回执（R32）：任务进度消费的事实账——uk(quest_code, event_id) 保证同一业务事实
 * 至多消费一次；source_time 为事实发生时间，进度计入事实归属业务日（历史事实仅在该日
 * 任务行已存在时补算），保证"从活动/消息/陪伴事实可重建、不把历史行为加到今天"。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_quest_event_receipt")
public class PetQuestEventReceipt {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long petId;

    /** 任务类型（PetQuestType.name） */
    private String questCode;

    /** 业务事实唯一键 */
    private String eventId;

    /** 事实发生时间(UTC) */
    private LocalDateTime sourceTime;

    /** 实际计入的业务日 */
    private LocalDate businessDate;

    private Integer amount;

    /** APPLIED / SKIPPED_STALE */
    private String status;

    /** PET-10：投影已尝试次数（重放/调度重试累加） */
    @TableField("attempts")
    private Integer attempts;

    /** PET-10：下次自动重试时间(UTC)；NULL 表示无待重试 */
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    /** PET-10：最近一次投影失败原因（截断 500 字符） */
    @TableField("last_error")
    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
