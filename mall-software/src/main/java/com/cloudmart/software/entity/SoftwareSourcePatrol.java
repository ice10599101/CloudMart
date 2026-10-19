package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 来源健康巡检记录（SF07）：仅自动检查明确允许的 HTTPS 来源；
 * 留历史，同指纹最新一条为准；结论写入 source_state 时不覆盖人工状态。
 */
@Getter
@Setter
@TableName("software_source_patrol")
public class SoftwareSourcePatrol {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    /** 稳定来源键 */
    private String sourceKey;

    /** 检查时来源内容指纹（与当前不一致则结论不适用） */
    private String sourceFingerprint;

    private SoftwareSourceType sourceType;

    /** 检查目标主机（审计可见，不含完整 URL） */
    private String urlHost;

    /** 结论：AVAILABLE/INVALID/UNKNOWN */
    private String outcome;

    /** 结论原因：DNS_FAILED/CONN_REFUSED/TIMEOUT/HTTP_ERROR/RATE_LIMITED/OK 等 */
    private String reasonCode;

    private Integer httpStatus;

    private Integer redirectCount;

    private Integer elapsedMs;

    private LocalDateTime checkedAt;

    /** 下次计划检查时间（按结论退避） */
    private LocalDateTime nextCheckAt;

    private LocalDateTime createdAt;
}
