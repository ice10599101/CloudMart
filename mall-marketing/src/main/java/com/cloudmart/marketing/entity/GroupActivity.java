package com.cloudmart.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("group_activities")
public class GroupActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private Long productId;
    private Long skuId;
    private BigDecimal originalPrice;
    private BigDecimal groupPrice;
    private Integer targetNumber;
    private Integer maxGroups;
    private Integer currentGroups;
    private Integer perUserLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
