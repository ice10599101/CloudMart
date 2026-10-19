package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 软件需求（UX06）：SOFTWARE_DISCOVERY 结构化求助。
 * 独立轻表（无奖励/成长联动——Wish 提交链路默认发奖励，违反 UX06 硬约束）；
 * 审核复用软件域内容审核流程；已找到目标必须为可见软件，
 * 下架后前台标注"已找到的软件暂不可用"，不继续展示有效获取承诺。
 */
@Data
@TableName("software_request")
public class SoftwareRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** 用途描述（要完成什么、什么场景） */
    private String usageDesc;

    private String mustHaveFeatures;

    private String platformScope;

    private String acceptableLicense;

    /** 来源搜索关键词（搜索无结果回跳保留） */
    private String sourceKeyword;

    /** OPEN/ANSWERED/CLOSED */
    private String status;

    /** PENDING/APPROVED/REJECTED */
    private String auditStatus;

    private String auditReason;

    private Long matchedSoftwareId;

    private String matchedReason;

    private LocalDateTime matchedAt;

    private Long matchedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
