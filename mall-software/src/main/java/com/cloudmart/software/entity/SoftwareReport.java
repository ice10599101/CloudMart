package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.SoftwareReportStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 软件举报（方案 6.12）：落 software_report 软件库内事务处置；
 * 目标须属于该软件（服务端校验）；证据仅本人素材（bizType=SOFTWARE_REPORT）。
 */
@Getter
@Setter
@TableName("software_report")
public class SoftwareReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    private Long softwareId;

    /** SOFTWARE/SOFTWARE_COMMENT */
    private String targetType;

    private Long targetId;

    /** 举报理由码（侵权/恶意内容/违规评论等） */
    private String reason;

    private String description;

    /** 证据素材 ID JSON 数组 */
    private String evidenceAssetIds;

    private SoftwareReportStatus status;

    private Long handlerId;

    private String handleNote;

    private LocalDateTime handledAt;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
