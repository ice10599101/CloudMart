package com.cloudmart.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("group_members")
public class GroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupOrderId;
    private Long userId;
    private Long activityId;
    private Boolean isLeader;
    private Long orderId;
    private String status;
    private LocalDateTime joinedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
