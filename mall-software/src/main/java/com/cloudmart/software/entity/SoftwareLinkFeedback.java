package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.LinkFeedbackStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 链接失效反馈（方案 6.9）：同指纹同人去重；3 人/24h 标 SUSPECTED；
 * 修复通过新快照上线后 RESOLVED 保留 original 指纹与修复 revisionId。
 */
@Getter
@Setter
@TableName("software_link_feedback")
public class SoftwareLinkFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private String sourceKey;

    /** 反馈时来源行 ID 快照（来源可能已随快照更替） */
    private Long sourceIdSnapshot;

    /** 反馈时来源内容指纹（去重键之一） */
    private String sourceFingerprint;

    /** SF02：兼容性反馈定位的业务版本键（链接类反馈为空） */
    private String releaseKey;

    /** SF02：反馈时的平台（兼容性反馈） */
    private String platform;

    /** SF02：反馈时的架构（兼容性反馈） */
    private String arch;

    /** SF02：系统版本文本（用户自述，非设备指纹） */
    private String systemVersionText;

    private Long reporterId;

    /** LINK_INVALID/CODE_WRONG/FILE_MISMATCH/SUSPICIOUS/OTHER */
    private String reasonCode;

    private String description;

    private LinkFeedbackStatus status;

    private Long handlerId;

    private String handleNote;

    /** 修复上线快照 ID（RESOLVED 时记录） */
    private Long resolvedRevisionId;

    private LocalDateTime handledAt;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
