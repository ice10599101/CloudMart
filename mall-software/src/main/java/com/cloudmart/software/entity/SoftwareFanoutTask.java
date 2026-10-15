package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订阅 fanout 任务（方案 9.4，P1）：每次更新发布一条；
 * 游标 cursor_subscription_id 分批扫描订阅，批次插入投递行与推进游标同事务。
 */
@Getter
@Setter
@TableName("software_fanout_task")
public class SoftwareFanoutTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long revisionId;

    private Integer publicationSeq;

    private LocalDateTime publishedAt;

    private Long cursorSubscriptionId;

    /** PENDING/RUNNING/DONE/FAILED */
    private String status;

    private String leaseOwner;

    private LocalDateTime leaseUntil;

    private Integer leaseVersion;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
