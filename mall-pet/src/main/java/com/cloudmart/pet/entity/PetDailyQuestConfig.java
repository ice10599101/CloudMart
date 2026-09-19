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
 * 宠物每日任务配置（每日按启用配置为每只宠物生成进度行）。
 *
 * <p>{@code questType} 必须与业务埋点一一对应：后台新增任务只需要选一个已埋点的口径，
 * 不需要改代码（与社区宠物活动的"惰性统计"同一设计取向）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_daily_quest_config")
public class PetDailyQuestConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一编码 */
    private String code;

    /** 任务名 */
    private String name;

    /** 任务描述 */
    private String description;

    /** 图标（emoji 或 URL） */
    private String icon;

    /** 统计口径（FEED/PLAY/.../COMPANION/DECORATE） */
    private String questType;

    /** 目标次数 */
    private Integer targetValue;

    /** 奖励经验 */
    private Integer expReward;

    /** 奖励星光 */
    private Integer currencyReward;

    /** 宠物等级门槛 */
    private Integer requiredLevel;

    /** 是否启用（1 启用/0 停用） */
    private Boolean enabled;

    /** 排序（升序） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
