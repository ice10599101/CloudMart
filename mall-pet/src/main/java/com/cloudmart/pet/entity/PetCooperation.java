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
@TableName("pet_cooperation")
public class PetCooperation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 好友合作周任务（N06）：每用户每自然周最多一支（双方各 uk）。 */
    private java.time.LocalDate weekStart;
    private Long inviterUserId;
    private Long inviteeUserId;
    /** INVITED / ACTIVE / COMPLETED / ENDED */
    private String status;
    private Long inviterPetId;
    private Long inviteePetId;
    private java.time.LocalDateTime inviteExpiresAt;
    private java.time.LocalDateTime acceptedAt;
    /** 双方贡献计数 JSON: {inviter:n, invitee:n} */
    private String contributions;
    private java.time.LocalDateTime endedAt;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
