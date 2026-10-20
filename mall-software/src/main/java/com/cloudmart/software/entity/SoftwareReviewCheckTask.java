package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SW04：提交后的自动审核检查任务（先冻结再异步检查）。
 *
 * <p>revision_id 唯一——同一不可变提交最多一份检查任务；远程检查通过稳定的
 * operation_key 幂等；结果回写必须以 CHECKING 状态与内容摘要复核为前提。</p>
 */
@Getter
@Setter
@TableName("software_review_check_task")
public class SoftwareReviewCheckTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long revisionId;

    /** 提交幂等键（远程检查幂等身份，重试不生成新身份） */
    private String operationKey;

    /** 冻结内容摘要（检查前复核必须与 revision.content_digest 一致） */
    private String contentDigest;

    /** 提交时具有直接发布权限：检查通过且无需人工复核时自动上线 */
    private Boolean autoPublish;

    /** PENDING/RUNNING/DONE/REJECTED/DEAD_LETTER/CANCELLED */
    private String status;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String lockedBy;

    private LocalDateTime lockedAt;

    private LocalDateTime leaseUntil;

    private Integer leaseVersion;

    private String lastError;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
