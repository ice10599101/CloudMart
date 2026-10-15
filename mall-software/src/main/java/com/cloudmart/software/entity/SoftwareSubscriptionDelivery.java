package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订阅更新投递（方案 9.4，P1）：唯一键 (software_id,publication_seq,user_id)；
 * 通知服务按 eventId+recipientId 稳定去重，人工重试保留同一去重键。
 */
@Getter
@Setter
@TableName("software_subscription_delivery")
public class SoftwareSubscriptionDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Integer publicationSeq;

    private Long userId;

    /** PENDING/SENT/CANCELLED/FAILED */
    private String status;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String leaseOwner;

    private LocalDateTime leaseUntil;

    private Integer leaseVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
