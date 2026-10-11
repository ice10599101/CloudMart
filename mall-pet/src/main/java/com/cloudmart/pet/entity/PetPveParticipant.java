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
 * PVE 参与占用（R03）：发起/加入共用；uk(user_id) 数据库唯一约束保证
 * 一个用户同时仅一个活动副本（切宠不可绕过），终态释放。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_pve_participant")
public class PetPveParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long runId;

    private Long petId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
