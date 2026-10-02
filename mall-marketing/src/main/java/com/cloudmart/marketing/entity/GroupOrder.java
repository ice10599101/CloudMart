package com.cloudmart.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.annotation.Version;
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

    /** T10：乐观锁版本——超时与最后一人加入竞争时终态迁移 CAS 裁决 */
    @Version
    private Integer version;

    private LocalDateTime expireTime;
    private LocalDateTime successTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
