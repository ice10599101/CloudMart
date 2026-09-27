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
 * 拜访事实（B01/BE-06）：三类拜访入口共享；uk(visitor,owner,businessDate) 保证
 * 同主人同业务日至多一次（不同宠物同主人仍只算一次）；reward_granted 由数据库额度裁决。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_visit_fact")
public class PetVisitFact {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long visitorUserId;

    private Long ownerUserId;

    private Long visitorPetId;

    private Long ownerPetId;

    /** 入口：NEIGHBOR/ROOM/FRIEND */
    private String source;

    private LocalDate businessDate;

    private Integer rewardGranted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
