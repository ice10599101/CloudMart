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
 * 社区宠物活动配置（原文档 §89 社区宠物活动）。
 *
 * <p>{@code startsAt/endsAt} 为空表示常驻；时间一律 UTC，窗口判定以服务端时间为准。
 * 进度由业务表惰性统计（不设写入路径累加器），避免 MQ/并发下的计数漂移。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_event_config")
public class PetEventConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    /** 统计口径：BOTTLE/BATTLE/WORK/STUDY/FEED/PLAY/VISIT */
    private String eventType;

    /** LIFETIME 累计 / WINDOW 限时（B16：不再靠起止时间是否为空猜测） */
    private String eventMode;

    /** 目标次数 */
    private Integer targetValue;

    private Integer rewardStarlight;

    private Integer rewardExp;

    /** 额外奖励物品编码（pet_equipment_config.code，可空） */
    private String rewardItemCode;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private Boolean enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
