package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SF08 补批：作者来源申诉。关联处置 ID 并保存处置快照，后台与原处置并列查看；
 * 同一来源同时仅允许一条 PENDING（数据库生成列唯一键保证）。
 */
@Getter
@Setter
@TableName("software_source_appeal")
public class SoftwareSourceAppeal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private String sourceKey;

    /** 关联处置ID（software_source_state.id） */
    private Long dispositionId;

    /** 申诉时的处置结论快照 */
    private String dispositionAvailability;

    private String dispositionReason;

    private LocalDateTime dispositionAt;

    private Long appellantId;

    private String reason;

    /** 纯文本证据说明（不接收受限链接/凭据） */
    private String evidence;

    /** PENDING/ACCEPTED/REJECTED */
    private String status;

    private Long handlerId;

    private String handleNote;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
