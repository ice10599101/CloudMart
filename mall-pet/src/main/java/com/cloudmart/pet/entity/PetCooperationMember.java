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
 * 合作周成员名额（R35）：UNIQUE(user_id, week_start) 在接受时原子写入双方——
 * 关闭"同人同周既当 inviter 又当 invitee"绕过双角色唯一键的组合缺口；
 * 邀请未接受不写本表（不消耗周名额）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_cooperation_member")
public class PetCooperationMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private LocalDate weekStart;

    private Long cooperationId;

    /** INVITER / INVITEE */
    private String role;

    /** 参与时绑定宠物 */
    private Long petId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
