package com.cloudmart.risk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("risk_records")
public class RiskRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String actionType;

    private String riskLevel;

    private String result;

    private Long ruleId;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
