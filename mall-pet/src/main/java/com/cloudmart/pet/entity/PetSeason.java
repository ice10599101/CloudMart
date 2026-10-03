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

/** 排行榜赛季（F2）：ACTIVE 进行中 / SETTLED 已结算（结算 CAS 防重）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_season")
public class PetSeason {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    /** 开始时间（UTC） */
    private LocalDateTime startsAt;

    /** 结束时间（UTC） */
    private LocalDateTime endsAt;

    /** ACTIVE / FREEZING / SETTLING / SETTLED（R06：SETTLED 只在全量发奖后写，失败不回退 ACTIVE） */
    private String status;

    private LocalDateTime settledAt;

    /** R06 冻榜完成时间（延迟冻榜时≠endsAt，界面须展示实际值） */
    private LocalDateTime freezeAt;

    /** R06 排名快照是否完整（1=完整；SETTLED 前置条件） */
    private Integer snapshotComplete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
