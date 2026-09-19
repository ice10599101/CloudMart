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
 * 宠物捞瓶流水。{@code bottleId} 指向 mall-wish {@code wish_drift_bottle.id}——
 * 不冗余瓶内容、不建第二套漂流瓶（原文档 §15 硬边界）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_bottle_record")
public class PetBottleRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long petId;

    private Long userId;

    /** 关联 pet_activity.id（BOTTLE_FISHING 任务） */
    private Long activityId;

    /** 捞到的漂流瓶 ID（wish_drift_bottle.id；EMPTY/FAILED 时为 null） */
    private Long bottleId;

    /** CAUGHT（成功捞起）/ EMPTY（空手而归：成功率未中或海里无瓶）/ FAILED（Feign 降级，可重试领取） */
    private String outcome;

    /** 稀有度：NORMAL（真实漂流瓶）/ RARE / PET / EASTER_EGG（服务端生成的特殊内容瓶） */
    private String rarity;

    /** 特殊瓶子内容文本（稀有瓶/宠物瓶/彩蛋瓶专属；普通瓶为 null，内容在 mall_wish） */
    private String specialContent;

    /** 本次成功率快照（0-1，便于回放与调参） */
    private Double successRate;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
