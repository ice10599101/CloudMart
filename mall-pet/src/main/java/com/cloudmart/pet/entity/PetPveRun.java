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
 * 协作 PVE 副本（§6/R03）：turnNo/nextActorUserId/version CAS 推进——
 * 日志条数不决定轮次；回合明细在 pet_pve_round_log（不落本表 JSON）。
 * 终态：WON/FAILED/EXPIRED/CANCELLED（reward_status 权威在 pet_reward_claim）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_pve_run")
public class PetPveRun {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_FIGHTING = "FIGHTING";
    public static final String STATUS_WON = "WON";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String REWARD_NONE = "NONE";
    public static final String REWARD_PENDING = "PENDING";
    public static final String REWARD_SETTLED = "SETTLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bossCode;

    private String bossName;

    private Integer bossMaxHp;

    private Integer bossHp;

    private Long initiatorUserId;

    private Long initiatorPetId;

    /** 发起人宠物战斗快照 JSON（Fighter） */
    private String initiatorSnapshot;

    private Long partnerUserId;

    private Long partnerPetId;

    private String partnerSnapshot;

    private Integer initiatorPetHp;

    private Integer partnerPetHp;

    private String status;

    /** 乐观锁版本（CAS 推进） */
    private Integer version;

    /** 已完成回合数（下一回合 = turnNo + 1） */
    private Integer turnNo;

    /** 当前行动方用户 ID（第 1 回合 = 发起人） */
    private Long nextActorUserId;

    /** 本回合行动截止（超时托管/失败收口依据） */
    private LocalDateTime turnDeadlineAt;

    /** 副本总时限（FIGHTING 超时批量收口） */
    private LocalDateTime runExpiresAt;

    /** 战斗规则版本（伤害口径/种子回放） */
    private String ruleVersion;

    /** 固定种子（开局快照 + 回合号决定性结算） */
    private Long seed;

    /** 奖励聚合状态（权威在 pet_reward_claim，bizType=PVE） */
    private String rewardStatus;

    /** 发起请求键（重放幂等） */
    private String initiatorRequestKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
