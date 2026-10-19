package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发布资格（方案 4.0/6.14）：默认关闭，记录缺失与 false 均禁止发布；
 * 数据库为最终事实，缓存仅用于 UI 提示；仅管理员逐用户开通。
 */
@Getter
@Setter
@TableName("software_publisher_permission")
public class SoftwarePublisherPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 前台会员 ID（不是后台管理员 ID；唯一键幂等首开） */
    private Long userId;

    private Boolean canPublish;

    /** 未指定时保守地要求人工审核，仅管理员可设置为 false。 */
    private Boolean reviewRequired = true;

    public String getPublishMode() {
        if (!Boolean.TRUE.equals(canPublish)) return "DENIED";
        return Boolean.FALSE.equals(reviewRequired) ? "DIRECT" : "REVIEW";
    }

    /** 最近操作管理员 ID */
    private Long updatedBy;

    /** 开通/关闭原因（审计要求） */
    private String reason;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
