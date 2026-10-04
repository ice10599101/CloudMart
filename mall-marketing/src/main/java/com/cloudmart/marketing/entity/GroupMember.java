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

    /** T11：参团时选定的收货地址快照（成团建单权威，不悄悄改用新默认地址） */
    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    /** T11 建单任务台账：PENDING/SUCCEEDED/FAILED_ADDRESS/FAILED/ABSENT_ADDRESS */
    private String orderTaskStatus;
    private String orderTaskError;
    private Long orderId;
    private String status;
    private LocalDateTime joinedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
