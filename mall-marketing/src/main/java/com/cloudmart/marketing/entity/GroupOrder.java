package com.cloudmart.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("group_orders")
public class GroupOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;
    private Long leaderUserId;
    private Integer currentNumber;
    private Integer targetNumber;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime successTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
