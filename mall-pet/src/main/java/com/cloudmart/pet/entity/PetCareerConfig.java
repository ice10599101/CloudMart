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
 * 宠物职业配置（职业路线 × 阶段）。
 *
 * <p>职业是"长期打工"：入职后完成职业工作累积次数，满足「次数 + 等级 + 星光」后
 * 晋升到 {@code promoteToCode}（NULL = 已是该路线最高阶）。数值全部服务端权威。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_career_config")
public class PetCareerConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一编码 */
    private String code;

    /** 职业名 */
    private String name;

    /** 职业描述 */
    private String description;

    /** 职业路线（同线内逐阶晋升） */
    private String careerLine;

    /** 阶段（1 初级/2 中级/3 高级） */
    private Integer tier;

    /** 图标（emoji 或 URL） */
    private String icon;

    /** 入职最低等级 */
    private Integer requiredLevel;

    /** 入职最低智力 */
    private Integer requiredIntelligence;

    /** 单次工作耗时（秒） */
    private Integer durationSeconds;

    /** 精力消耗 */
    private Integer energyCost;

    /** 饥饿消耗 */
    private Integer hungerCost;

    /** 基础经验奖励 */
    private Integer expReward;

    /** 基础星光奖励 */
    private Integer currencyReward;

    /** 晋升目标职业（NULL = 最高阶） */
    private String promoteToCode;

    /** 晋升所需本职业工作次数 */
    private Integer promoteRequiredCount;

    /** 晋升消耗星光 */
    private Integer promoteStarCost;

    /** 是否开放（1 开放/0 停招） */
    private Boolean enabled;

    /** 排序（升序） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
