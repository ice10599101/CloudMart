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
 * 宠物活动进度（进度值由业务表惰性统计后回写，{@code claimedAt} 非空即已领奖——幂等标记）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_event_progress")
public class PetEventProgress {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long petId;

    private Long userId;

    private String eventCode;

    /** 已完成次数（惰性统计落库快照） */
    private Integer progress;

    private LocalDateTime claimedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
