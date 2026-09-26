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
 * 用户每日收益额度（B06）：数据库权威，Redis 仅作快速限频。
 *
 * <p>主体=用户（切换宠物/入口不绕过）；targetId 支持按目标细分的额度
 * （如 PvP 对同一对手用户每天 1 场收益）。占用为原子条件更新，业务失败不回滚额度时
 * 由调用方显式释放（release），避免"状态验证失败永久吃掉额度"。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_daily_quota")
public class PetDailyQuota {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** FEED/PLAY_REWARD/REST_INTIMACY/BATTLE_REWARD/PVP_OPPONENT/WALL_POST/VISIT_REWARD/LIKE_REWARD/MINIGAME/DECORATE */
    private String quotaType;

    /** 目标维度（0=用户级全局） */
    private Long targetId;

    private LocalDate businessDate;

    private Integer used;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
