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

/** 用户屏蔽名单（B14）：uk(user_id, blocked_user_id) 幂等。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_user_block")
public class PetUserBlock {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long blockedUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
