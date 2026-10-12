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

    /** T11：不可核验跳过条数（依赖服务不可达等）——区分"干净"与"没查到" */
    private Integer totalSkipped;

    /** RUNNING / DONE / FAILED / PARTIAL（依赖不可达且已核对部分，保留断点同日续扫） */
    private String status;

    /** R26：租约持有者（执行实例标识）；NULL 表示尚未出租约（历史行） */
    private String leaseOwner;

    /** R26：租约到期时刻；RUNNING 超过该时刻可被其他执行器接管 */
    private LocalDateTime leaseUntil;

    /** R26：最近一个批次处理完的心跳时间 */
    private LocalDateTime heartbeatAt;

    /** R26：支付正向扫描断点（已处理最大 attemptId；恢复时 gt 此值继续） */
    private Long paymentCursor;

    /** R26：退款正向扫描断点（已处理最大 refundId） */
    private Long refundCursor;

    /** R26：库存正向扫描断点（已处理最大 orderId） */
    private Long inventoryCursor;

    /** R26：反向订单分页扫描断点（已完成页码；恢复时从 orderPage+1 开始） */
    private Integer orderPage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
