package com.cloudmart.risk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("risk_rules")
public class RiskRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String actionType;

    private String riskLevel;

    private Integer threshold;

    private Integer timeWindowMinutes;

    private Integer status;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
