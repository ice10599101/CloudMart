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
 * 协作 PVE 副本会话（§6）：发起（OPEN）→ 队友加入（FIGHTING）→ 攻击推进 →
 * WON/FAILED。双宠战斗快照与回合流水存本行（副本为临时会话，不拆明细表）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_pve_run")
public class PetPveRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bossCode;

    private String bossName;

    private Integer bossMaxHp;

    private Integer bossHp;

    private Long initiatorUserId;

    private Long initiatorPetId;

    /** Fighter JSON */
    private String initiatorSnapshot;

    private Long partnerUserId;

    private Long partnerPetId;

    /** Fighter JSON */
    private String partnerSnapshot;

    private Integer initiatorPetHp;

    private Integer partnerPetHp;

    /** 回合流水 JSON 数组 */
    private String rounds;

    /** OPEN/FIGHTING/WON/FAILED/EXPIRED */
    private String status;

    private Boolean rewardGranted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
