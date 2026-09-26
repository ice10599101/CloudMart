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


@Getter
@Setter
@NoArgsConstructor
@TableName("pet_minigame_round")
public class PetMinigameRound {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 接球小游戏局（N04）：服务端规则快照与随机序列，操作落库，幂等结算。 */
    private Long userId;
    private Long petId;
    private String gameType;
    /** ACTIVE / SETTLED / EXPIRED */
    private String status;
    private String ruleVersion;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime deadlineAt;
    /** 随机挑战序列 JSON */
    private String sequence;
    /** 已接受操作 JSON: [{seq,slot,windowIndex,serverTime}] */
    private String ops;
    private Integer successCount;
    private Boolean rewardEligible;
    private String rewardOperationId;
    private java.time.LocalDate quotaDate;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
