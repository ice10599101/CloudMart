package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("user_blocks")
public class UserBlock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long blockedUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
