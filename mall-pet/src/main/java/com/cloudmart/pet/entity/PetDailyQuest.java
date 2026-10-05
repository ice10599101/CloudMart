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
 * 宠物每日任务进度（{@code uk_pet_daily_quest}：pet × 日期 × 任务 唯一）。
 *
 * <p>进度由业务埋点累加（{@code progress = LEAST(progress + n, target)}），
 * 领奖走 CAS（{@code status='CLAIMED' WHERE id=? AND status='COMPLETE'}）幂等；
 * {@code targetValue} 生成时快照，后台改配置不影响当日已生成的任务（AGENTS §17）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_daily_quest")
public class PetDailyQuest {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 宠物 ID */
    private Long petId;

    /** 用户 ID（查询冗余） */
    private Long userId;

    /** 任务日期（UTC 自然日） */
    private LocalDate questDate;

    /** 所属任务集（PET-09：领取/展示/进度按集绑定与归属校验；存量回填后非空） */
    @TableField("set_id")
    private Long setId;

    /** 任务编码（pet_daily_quest_config.code） */
    private String questCode;

    /** 当前进度 */
    private Integer progress;

    /** 目标值（生成时快照） */
    private Integer targetValue;

    /** R32 生成时奖励快照 JSON（普通任务: name/questType/expReward/currencyReward/actionTarget；宝箱行: chestExp/chestCurrency） */
    private String rewardSnapshot;

    /** 状态：IN_PROGRESS/COMPLETE/CLAIMED/CANCELLED */
    private String status;

    /** 取消原因（管理端受审计命令必填，§8.2） */
    private String cancelReason;

    /** 取消操作管理员（服务令牌 admin_username，P0-3） */
    private String cancelledBy;

    /** 完成时间（UTC） */
    private LocalDateTime completedAt;

    /** 领奖时间（UTC，幂等标记） */
    private LocalDateTime claimedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
