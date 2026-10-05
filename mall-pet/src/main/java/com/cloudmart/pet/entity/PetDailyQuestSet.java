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
 * 宠物每日任务集（PET-09：每宠每业务日生成一次的冻结任务集合，{@code uk(pet_id, business_date)}）。
 *
 * <p>集生成后当日升级/配置编辑不扩大既有集合（{@code levelSnapshot} 冻结生成时等级）；
 * 领取保留至 {@code claimDeadline}（下一业务日结束，24h 宽限）；宝箱奖励生成时冻结到
 * {@code chestSnapshot}；集内任务行经 {@code pet_daily_quest.set_id} 绑定，领取/展示/进度
 * 全部按集归属校验，切换主宠不改变集归属。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_daily_quest_set")
public class PetDailyQuestSet {

    /** 主键（即任务集 setId，三端路由与深链接使用该实体 ID） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户 */
    @TableField("user_id")
    private Long userId;

    /** 绑定宠物 */
    @TableField("pet_id")
    private Long petId;

    /** 业务日（Asia/Shanghai） */
    @TableField("business_date")
    private LocalDate businessDate;

    /** 业务日时区（集生成时固定） */
    @TableField("timezone")
    private String timezone;

    /** 生成时宠物等级（当日升级不扩大既有集合） */
    @TableField("level_snapshot")
    private Integer levelSnapshot;

    /** 集生成时间（UTC） */
    @TableField("generated_at")
    private LocalDateTime generatedAt;

    /** 领取截止（下一业务日结束，24h 宽限） */
    @TableField("claim_deadline")
    private LocalDateTime claimDeadline;

    /** 状态：ACTIVE/EXPIRED */
    @TableField("status")
    private String status;

    /** 宝箱奖励快照（生成时冻结；legacy 集标记 {"legacy":true}） */
    @TableField("chest_snapshot")
    private String chestSnapshot;

    /** 宝箱领取时间 */
    @TableField("chest_claimed_at")
    private LocalDateTime chestClaimedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
