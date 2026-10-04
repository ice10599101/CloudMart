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

/** 期次领奖事实（R33）：uk(occurrence_id, pet_id)——同宠同期至多领一次。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_event_occurrence_claim")
public class PetEventOccurrenceClaim {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long occurrenceId;

    private String eventCode;

    private Long petId;

    private Long userId;

    /** 领奖时的奖励快照 */
    private String rewardSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
