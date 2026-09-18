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
 * 对战记录。异步 PvP：挑战时快照双方属性 + 随机种子落库（PENDING），
 * 防守方 accept 后按快照计算——防守方中途养成不影响已发起挑战的公平性；
 * seed + 快照使战斗可离线复现（客户端只播放动画，无权计算结果）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_battle")
public class PetBattle {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** PVE/PVP */
    private String mode;

    private Long attackerPetId;

    private Long attackerUserId;

    private Long defenderPetId;

    private Long defenderUserId;

    /** PENDING/FINISHED/DECLINED/EXPIRED */
    private String status;

    /** 战斗随机种子（结算前生成，保证可复现） */
    private Long seed;

    /** 胜者宠物 ID（平局/未结算为 null） */
    private Long winnerPetId;

    /** 挑战方宠物属性快照 JSON */
    private String attackerSnapshot;

    /** 防守方宠物属性快照 JSON（PvE 为野生宠物模板） */
    private String defenderSnapshot;

    /** 回合流水 JSON：[{round,actor,action,damage,critical,dodge,remainingHp}] */
    private String rounds;

    /** 经验奖励（结算后回填，双方各自发放） */
    private Integer expReward;

    /** 星光奖励（仅胜者；经 mall-wish 内部端点发放） */
    private Integer currencyReward;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
