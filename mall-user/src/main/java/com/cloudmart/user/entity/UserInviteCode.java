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

/** 邀请码（N-3 邀请裂变）：一人一码，首查生成。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_invite_code")
public class UserInviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String code;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
