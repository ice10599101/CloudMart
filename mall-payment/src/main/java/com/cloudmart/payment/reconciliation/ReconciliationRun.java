package com.cloudmart.payment.reconciliation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对账运行（OPS-01）：一次按日期+范围的核对执行记录。 */
@Data
@TableName("reconciliation_run")
public class ReconciliationRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate businessDate;

    /** PAYMENT_ORDER / REFUND / INVENTORY */
    private String scope;

    private Integer totalChecked;

    private Integer totalDiff;

    /** RUNNING / DONE / FAILED */
    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
