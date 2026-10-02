package com.cloudmart.payment.reconciliation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账服务（OPS-01/T01）：支付尝试 ↔ 订单一致性的持久化核对。
 *
 * <p>唯一真值源为 payment_attempt 台账（旧 payments 表已删除，T01）。
 * 核对规则（首期覆盖资金最高风险面）：</p>
 * <ul>
 *   <li>PAYMENT_SUCCESS_ORDER_NOT_PAID：支付 SUCCESS 但订单未 PAID——资金已收、
 *       履约未启动，HIGH；</li>
 *   <li>ORDER_PAID_NO_SUCCESS_PAYMENT：订单 PAID 但无 SUCCESS 支付记录——
 *       状态推进无资金凭证，HIGH。</li>
 * </ul>
 *
 * <p>扫描语义（T01：按游标遍历全部，不能只取首批）：两个核对方向均按 id 游标
 * 分页推进，直至扫描窗口耗尽。订单服务不可达的条目跳过不误报（差异必须两侧
 * 状态可核验才成立）。差异按 (run, diff_type, biz_id) 唯一——重复对账不产生重复差异。</p>
 */
@Slf4j
@Service
public class ReconciliationService {

    private final PaymentAttemptMapper attemptMapper;
    private final ReconciliationRunMapper runMapper;
    private final ReconciliationDifferenceMapper differenceMapper;
    private final OrderFeignClient orderFeignClient;
    private final int batchSize;

    public ReconciliationService(PaymentAttemptMapper attemptMapper,
                                 ReconciliationRunMapper runMapper,
                                 ReconciliationDifferenceMapper differenceMapper,
                                 OrderFeignClient orderFeignClient,
                                 com.cloudmart.payment.repository.RefundOrderMapper refundOrderMapper,
                                 com.cloudmart.payment.feign.InventoryReconFeignClient inventoryReconFeignClient,
                                 @Value("${ops.reconciliation.batch-size:200}") int batchSize) {
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
        this.differenceMapper = differenceMapper;
        this.orderFeignClient = orderFeignClient;
        this.refundOrderMapper = refundOrderMapper;
        this.inventoryReconFeignClient = inventoryReconFeignClient;
        this.batchSize = batchSize;
    }

    private final com.cloudmart.payment.repository.RefundOrderMapper refundOrderMapper;
    private final com.cloudmart.payment.feign.InventoryReconFeignClient inventoryReconFeignClient;

    /** FAILED 重试的运行租约：execute=false 时 run 为应直接返回的既有运行（DONE/RUNNING 幂等） */
    private record RunLease(ReconciliationRun run, boolean execute) {}

    /**
     * 幂等进入对账运行：DONE/RUNNING 返回既有运行（当日不重扫，防调度与手动并发双跑）；
     * FAILED 复用 uk(business_date,scope) 约束下的同一运行行 CAS 认领重置（当日可重试），
     * 并清理上次中断留下的半程差异（重扫后全量重建，不残留过期证据）。
     */
    private RunLease beginRun(LocalDate businessDate, String scope, String logTag) {
        ReconciliationRun existing = runMapper.findByDateAndScope(businessDate, scope);
        if (existing != null && "FAILED".equals(existing.getStatus())) {
            log.warn("[{}] 本业务日 {} 对账上次 FAILED（run={}），CAS 认领重置重试", logTag, scope, existing.getId());
            if (runMapper.claimFailedRun(existing.getId()) == 0) {
                // 并发下已被其他线程认领：视同 RUNNING，幂等返回
                log.info("[{}] 本业务日 {} 对账已被并发执行认领（run={}），幂等返回", logTag, scope, existing.getId());
                return new RunLease(runMapper.selectById(existing.getId()), false);
            }
            differenceMapper.delete(new LambdaQueryWrapper<ReconciliationDifference>()
                    .eq(ReconciliationDifference::getRunId, existing.getId()));
            existing.setStatus("RUNNING");
            existing.setTotalChecked(null);
            existing.setTotalDiff(null);
            existing.setStartedAt(LocalDateTime.now());
            existing.setFinishedAt(null);
            return new RunLease(existing, true);
        }
        if (existing != null) {
            log.info("[{}] 本业务日 {} 对账已执行（status={}，幂等返回既有运行 run={}）",
                    logTag, scope, existing.getStatus(), existing.getId());
            return new RunLease(existing, false);
        }
        ReconciliationRun run = new ReconciliationRun();
        run.setBusinessDate(businessDate);
        run.setScope(scope);
        run.setStatus("RUNNING");
        // started_at 库有默认值，但执行响应直接返回实体——补齐时间语义，避免响应缺时间戳
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        return new RunLease(run, true);
    }

    /** 执行一次支付↔订单对账（扫描最近 N 天 SUCCESS 支付核对订单状态）。 */
    public ReconciliationRun runPaymentOrderReconciliation(int scanDays) {
        LocalDate businessDate = LocalDate.now();
        RunLease lease = beginRun(businessDate, "PAYMENT_ORDER", "OPS01");
        if (!lease.execute()) {
            return lease.run();
        }
        ReconciliationRun run = lease.run();

        try {
            int checked = 0;
            int diffs = 0;
            LocalDateTime since = LocalDateTime.now().minusDays(scanDays);

            // 核对一：SUCCESS 支付的订单必须已 PAID+（id 游标全量扫描，T01）
            long lastId = 0;
            while (true) {
                List<PaymentAttempt> batch = attemptMapper.selectList(
                        new LambdaQueryWrapper<PaymentAttempt>()
                                .eq(PaymentAttempt::getStatus, "SUCCESS")
                                .ge(PaymentAttempt::getCreatedAt, since)
                                .gt(PaymentAttempt::getId, lastId)
                                .orderByAsc(PaymentAttempt::getId)
                                .last("LIMIT " + batchSize));
                if (batch.isEmpty()) {
                    break;
                }
                for (PaymentAttempt attempt : batch) {
                    lastId = attempt.getId();
                    checked++;
                    String orderStatus;
                    try {
                        ApiResponse<OrderInternalInfoDTO> orderResp =
                                orderFeignClient.getOrderInfo(attempt.getOrderId());
                        orderStatus = orderResp != null && orderResp.success() && orderResp.data() != null
                                ? orderResp.data().status() : "UNREACHABLE";
                    } catch (Exception queryError) {
                        // 订单服务不可达：该条跳过不误报（差异必须两侧状态可核验才成立）
                        log.warn("[OPS01] 订单状态查询失败，跳过该条 attemptId={}: {}",
                                attempt.getId(), queryError.getMessage());
                        continue;
                    }
                    if (isPaidOrBeyond(orderStatus)) {
                        continue;
                    }
                    diffs += recordDiff(run.getId(), "PAYMENT_SUCCESS_ORDER_NOT_PAID",
                            String.valueOf(attempt.getId()), "HIGH",
                            "支付 SUCCESS 但订单状态: " + orderStatus,
                            "{\"paymentStatus\":\"SUCCESS\",\"orderStatus\":\"" + orderStatus
                                    + "\",\"orderId\":" + attempt.getOrderId() + "}");
                }
                if (batch.size() < batchSize) {
                    break;
                }
            }

            // 核对二：PAID/SHIPPED/COMPLETED 订单必须有 SUCCESS 支付记录——
            // 订单状态由订单侧分页扫描（经内部端点逐页），本地按 orderId 查支付
            int page = 1;
            while (true) {
                ApiResponse<OrderFeignClient.PageDTO> paidPage = orderFeignClient
                        .listPaidOrders(page, batchSize);
                if (paidPage == null || !paidPage.success() || paidPage.data() == null
                        || paidPage.data().records().isEmpty()) {
                    break;
                }
                for (OrderInternalInfoDTO order : paidPage.data().records()) {
                    checked++;
                    Long count = attemptMapper.selectCount(new LambdaQueryWrapper<PaymentAttempt>()
                            .eq(PaymentAttempt::getOrderId, order.orderId())
                            .eq(PaymentAttempt::getStatus, "SUCCESS"));
                    if (count != null && count > 0) {
                        continue;
                    }
                    diffs += recordDiff(run.getId(), "ORDER_PAID_NO_SUCCESS_PAYMENT",
                            String.valueOf(order.orderId()), "HIGH",
                            "订单状态 " + order.status() + " 但无 SUCCESS 支付记录",
                            "{\"orderStatus\":\"" + order.status() + "\",\"orderId\":"
                                    + order.orderId() + "}");
                }
                if (paidPage.data().records().size() < batchSize) {
                    break;
                }
                page++;
            }

            run.setTotalChecked(checked);
            run.setTotalDiff(diffs);
            run.setStatus("DONE");
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            log.info("[OPS01] 对账完成 run={} checked={} diffs={}", run.getId(), checked, diffs);
            return run;
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            log.error("[OPS01] 对账执行失败 run={}", run.getId(), e);
            throw e;
        }
    }

    /**
     * T11：退款层对账—— refund_order 台账（SUCCEEDED）与订单状态双向核对：
     *   ① 退款单 SUCCEEDED → 订单必须 REFUNDED（未推进=事件丢失，HIGH）；
     *   ② 订单 REFUNDED → 必须存在 SUCCEEDED 退款单（无台账=资金事实缺失，HIGH）。
     * 渠道（provider）层流水首期 MOCK 同构同步确认，真实渠道接入后补渠道层核对。
     */
    public ReconciliationRun runRefundReconciliation(int scanDays) {
        LocalDate businessDate = LocalDate.now();
        RunLease lease = beginRun(businessDate, "REFUND", "T11");
        if (!lease.execute()) {
            return lease.run();
        }
        ReconciliationRun run = lease.run();

        try {
            int checked = 0;
            int diffs = 0;
            LocalDateTime since = LocalDateTime.now().minusDays(scanDays);

            // ① 退款单 SUCCEEDED → 订单 REFUNDED
            long lastId = 0;
            while (true) {
                List<com.cloudmart.payment.entity.RefundOrder> batch =
                        refundOrderMapper.scanForReconciliation(since, lastId, batchSize);
                if (batch.isEmpty()) {
                    break;
                }
                for (com.cloudmart.payment.entity.RefundOrder refund : batch) {
                    lastId = refund.getId();
                    if (!"SUCCEEDED".equals(refund.getStatus())) {
                        continue;
                    }
                    checked++;
                    String orderStatus;
                    try {
                        ApiResponse<OrderInternalInfoDTO> orderResp =
                                orderFeignClient.getOrderInfo(refund.getOrderId());
                        orderStatus = orderResp != null && orderResp.success() && orderResp.data() != null
                                ? orderResp.data().status() : "UNREACHABLE";
                    } catch (Exception queryError) {
                        log.warn("[T11] 退款对账订单查询失败，跳过 refundNo={}: {}",
                                refund.getRefundNo(), queryError.getMessage());
                        continue;
                    }
                    if ("REFUNDED".equals(orderStatus)) {
                        continue;
                    }
                    diffs += recordDiff(run.getId(), "REFUND_SUCCEEDED_ORDER_NOT_REFUNDED",
                            refund.getRefundNo(), "HIGH",
                            "退款 SUCCEEDED 但订单状态: " + orderStatus,
                            "{\"refundNo\":\"" + refund.getRefundNo()
                                    + "\",\"orderStatus\":\"" + orderStatus
                                    + "\",\"orderId\":" + refund.getOrderId() + "}");
                }
                if (batch.size() < batchSize) {
                    break;
                }
            }

            // ② REFUNDED 订单 → 必须有 SUCCEEDED 退款单（订单侧经分页内部端点）
            int page = 1;
            while (true) {
                ApiResponse<OrderFeignClient.PageDTO> paidPage = orderFeignClient
                        .listPaidOrders(page, batchSize);
                if (paidPage == null || !paidPage.success() || paidPage.data() == null
                        || paidPage.data().records().isEmpty()) {
                    break;
                }
                for (OrderInternalInfoDTO order : paidPage.data().records()) {
                    if (!"REFUNDED".equals(order.status())) {
                        continue;
                    }
                    checked++;
                    Long succeeded = refundOrderMapper.selectCount(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.payment.entity.RefundOrder>()
                                    .eq(com.cloudmart.payment.entity.RefundOrder::getOrderId, order.orderId())
                                    .eq(com.cloudmart.payment.entity.RefundOrder::getStatus, "SUCCEEDED"));
                    if (succeeded != null && succeeded > 0) {
                        continue;
                    }
                    diffs += recordDiff(run.getId(), "ORDER_REFUNDED_NO_SUCCEEDED_REFUND",
                            String.valueOf(order.orderId()), "HIGH",
                            "订单 REFUNDED 但无 SUCCEEDED 退款单",
                            "{\"orderStatus\":\"REFUNDED\",\"orderId\":" + order.orderId() + "}");
                }
                if (paidPage.data().records().size() < batchSize) {
                    break;
                }
                page++;
            }

            run.setTotalChecked(checked);
            run.setTotalDiff(diffs);
            run.setStatus("DONE");
            run.setFinishedAt(java.time.LocalDateTime.now());
            runMapper.updateById(run);
            log.info("[T11] 退款对账完成 run={} checked={} diffs={}", run.getId(), checked, diffs);
            return run;
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setFinishedAt(java.time.LocalDateTime.now());
            runMapper.updateById(run);
            log.error("[T11] 退款对账执行失败 run={}", run.getId(), e);
            throw e;
        }
    }

    /**
     * T11：库存层对账——预占台账与订单状态双向核对：
     *   ① 台账 RESERVED 且预占创建超 24h → 订单必须非 CLOSED/非已取消
     *      （台账滞留=确认/释放事件丢失，HIGH；订单不存在=MEDIUM）；
     *   ② 台账 RELEASED/CONFIRMED 但订单 CANCELLED/REFUNDED 与 CONFIRMED 矛盾
     *      （CONFIRMED+取消订单=资金货两失风险，HIGH）。
     * 台账查询失败 fail-closed 跳过该批（不误报），差异唯一键防重。
     */
    public ReconciliationRun runInventoryReconciliation(int scanDays) {
        LocalDate businessDate = LocalDate.now();
        RunLease lease = beginRun(businessDate, "INVENTORY", "T11");
        if (!lease.execute()) {
            return lease.run();
        }
        ReconciliationRun run = lease.run();

        try {
            int checked = 0;
            int diffs = 0;
            LocalDateTime since = LocalDateTime.now().minusDays(scanDays);
            long lastId = 0;
            while (true) {
                // 台账扫描失败必须显式 FAIL（run=FAILED），不得按空批顺延——
                // "扫不到"与"没有可核对的行"对运营是两个不同的事实，静默 0 会掩盖调用链故障
                ApiResponse<java.util.List<com.cloudmart.payment.dto.ReservationScanDTO>> scanResp =
                        inventoryReconFeignClient.scanReservations(since, lastId, batchSize);
                if (scanResp == null || !scanResp.success() || scanResp.data() == null) {
                    throw new IllegalStateException("[T11] 库存台账扫描返回失败信封: "
                            + (scanResp == null || scanResp.error() == null ? "null"
                            : scanResp.error().code() + " " + scanResp.error().message()));
                }
                java.util.List<com.cloudmart.payment.dto.ReservationScanDTO> batch = scanResp.data();
                if (batch.isEmpty()) {
                    break;
                }
                for (com.cloudmart.payment.dto.ReservationScanDTO row : batch) {
                    lastId = Math.max(lastId, row.orderId() == null ? lastId : row.orderId());
                    checked++;
                    String orderStatus;
                    try {
                        ApiResponse<OrderInternalInfoDTO> orderResp =
                                orderFeignClient.getOrderInfo(row.orderId());
                        orderStatus = orderResp != null && orderResp.success() && orderResp.data() != null
                                ? orderResp.data().status() : "UNREACHABLE";
                    } catch (Exception queryError) {
                        log.warn("[T11] 库存对账订单查询失败，跳过 orderId={}: {}",
                                row.orderId(), queryError.getMessage());
                        continue;
                    }
                    if ("UNREACHABLE".equals(orderStatus)) {
                        continue;
                    }
                    if ("RESERVED".equals(row.status()) && !row.createdAt().isBefore(LocalDateTime.now().minusHours(24))) {
                        continue; // 24h 内的新预占：正常在途
                    }
                    boolean orderGone = "CANCELLED".equals(orderStatus) || "REFUNDED".equals(orderStatus);
                    if ("RESERVED".equals(row.status()) && orderGone) {
                        diffs += recordDiff(run.getId(), "RESERVATION_STUCK_ORDER_CLOSED",
                                String.valueOf(row.orderId()), "HIGH",
                                "预占滞留 RESERVED 但订单 " + orderStatus,
                                "{\"reservationStatus\":\"RESERVED\",\"orderStatus\":\"" + orderStatus
                                        + "\",\"orderId\":" + row.orderId() + "}");
                        continue;
                    }
                    if ("RESERVED".equals(row.status()) && orderStatus.equals("UNREACHABLE")) {
                        continue;
                    }
                    if ("CONFIRMED".equals(row.status()) && orderGone) {
                        diffs += recordDiff(run.getId(), "RESERVATION_CONFIRMED_ORDER_CANCELLED",
                                String.valueOf(row.orderId()), "HIGH",
                                "台账 CONFIRMED 但订单 " + orderStatus + "（资金货两失风险）",
                                "{\"reservationStatus\":\"CONFIRMED\",\"orderStatus\":\"" + orderStatus
                                        + "\",\"orderId\":" + row.orderId() + "}");
                    }
                }
                if (batch.size() < batchSize) {
                    break;
                }
            }

            run.setTotalChecked(checked);
            run.setTotalDiff(diffs);
            run.setStatus("DONE");
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            log.info("[T11] 库存对账完成 run={} checked={} diffs={}", run.getId(), checked, diffs);
            return run;
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            log.error("[T11] 库存对账执行失败 run={}", run.getId(), e);
            throw e;
        }
    }

    private boolean isPaidOrBeyond(String orderStatus) {
        return "PAID".equals(orderStatus) || "SHIPPED".equals(orderStatus)
                || "COMPLETED".equals(orderStatus) || "REFUNDING".equals(orderStatus)
                || "REFUNDED".equals(orderStatus);
    }

    /** 差异唯一（run+type+biz_id），重复对账不产生重复差异。 */
    private int recordDiff(Long runId, String type, String bizId, String severity,
                           String detail, String evidence) {
        Long exists = differenceMapper.selectCount(new LambdaQueryWrapper<ReconciliationDifference>()
                .eq(ReconciliationDifference::getRunId, runId)
                .eq(ReconciliationDifference::getDiffType, type)
                .eq(ReconciliationDifference::getBizId, bizId));
        if (exists != null && exists > 0) {
            return 0;
        }
        ReconciliationDifference diff = new ReconciliationDifference();
        diff.setRunId(runId);
        diff.setDiffType(type);
        diff.setBizId(bizId);
        diff.setSeverity(severity);
        diff.setDetail(detail);
        diff.setEvidence(evidence);
        diff.setResolveStatus("OPEN");
        differenceMapper.insert(diff);
        log.warn("[OPS01] 对账差异 type={} bizId={} detail={}", type, bizId, diff.getDetail());
        return 1;
    }
}
