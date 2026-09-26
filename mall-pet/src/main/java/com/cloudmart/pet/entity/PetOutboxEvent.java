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
 * 事务性事件发件箱（B01/B19）：业务事务内写入，提交后由发送器异步投递 MQ。
 *
 * <p>eventId 为确定性业务事件键（TYPE:实例），业务回滚时行一并回滚（不产生假事件），
 * 发送失败按退避重试；消费者按 eventId 去重（MQ at-least-once 语义兜底）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_outbox_event")
public class PetOutboxEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventId;

    /** 事件类型（=MQ tag） */
    private String eventType;

    private Long userId;

    private Long petId;

    /** 消息体 JSON（PetEventMessage 结构，Long 一律字符串） */
    private String payload;

    /** NEW/SENT/FAILED */
    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
