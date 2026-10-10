package com.cloudmart.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 邀请绑定关系（N-3）：一人只可被邀请一次（uk_invitee），禁止自邀。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_invite_relation")
public class UserInviteRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inviterId;

    private Long inviteeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
