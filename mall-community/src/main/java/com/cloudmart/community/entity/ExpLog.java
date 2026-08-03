package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("exp_logs")
public class ExpLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer expChange;

    private String source;

    private Long bizId;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
