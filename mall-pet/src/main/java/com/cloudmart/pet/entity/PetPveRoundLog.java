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
 * PVE 回合明细（R03）：独立追加表，替代 VARCHAR(8000) rounds JSON——
 * 长战斗不再超长；(run_id, turn_no, seq) 唯一，(run_id, request_key) 重放判重。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_pve_round_log")
public class PetPveRoundLog {

    public static final String ACTION_ATTACK = "ATTACK";
    public static final String ACTION_COUNTER = "COUNTER";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Integer turnNo;

    private Integer seq;

    private Long actorUserId;

    private Long actorPetId;

    private String actorName;

    private String action;

    private Integer damage;

    private Boolean critical;

    private Boolean dodged;

    private String targetName;

    private Integer targetHpAfter;

    /** 攻击请求键（仅 seq=1 行携带） */
    private String requestKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
