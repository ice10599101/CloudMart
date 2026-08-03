package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("level_configs")
public class LevelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer level;

    private String title;

    private Integer minExp;

    private String icon;

    private String benefits;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
