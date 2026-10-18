package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发布者申请（SF03）：用户申请发布资格 → 后台审核 → 通过时同事务授予资格并写审计。
 * 未结申请唯一由 (user_id, pending_slot) 生成列唯一键保证，不靠"先查再插"。
 */
@Getter
@Setter
@TableName("software_publisher_application")
public class SoftwarePublisherApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String status;

    private String intro;

    private String intendedCategories;

    /** 本人已上传的佐证素材 ID 数组（JSON），可空 */
    private String evidenceAssetIds;

    private LocalDateTime submittedAt;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String reason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
