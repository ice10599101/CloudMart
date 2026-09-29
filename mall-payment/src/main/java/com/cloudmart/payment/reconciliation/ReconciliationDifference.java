package com.cloudmart.payment.reconciliation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对账差异（OPS-01）：单条不一致记录，人工处置不改资金只登记证据。 */
@Data
@TableName("reconciliation_difference")
public class ReconciliationDifference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String diffType;

    private String bizId;

    /** HIGH / MEDIUM / LOW */
    private String severity;

    private String detail;

    private String evidence;

    /** OPEN / RESOLVED / ACCEPTED */
    private String resolveStatus;

    private Long resolvedBy;

    private String resolveNote;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
