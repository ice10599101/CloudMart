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

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 对账服务（OPS-01/T01/R26）：支付尝试 ↔ 订单一致性的持久化核对。
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
 * <p>R26 可靠性语义：</p>
 * <ul>
 *   <li>执行租约：RUNNING 持约者每批心跳续期；进程 kill/崩溃后租约到期，
 *       同日下次触发由其他执行器 CAS 接管，调度重复不双跑；</li>
 *   <li>断点续扫：正向 last-id 游标、反向已完成页码逐批持久化，
 *       PARTIAL/FAILED/失联接管均从断点继续，已核对计数不重计；</li>
 *   <li>依赖分级：单笔状态查询不可达计 skipped 不误报；分页/台账端点不可达
 *       （区别于"真空页"）→ PARTIAL 而非 DONE/FAILED，保留断点同日续扫；</li>
 *   <li>差异证据不删：重试/接管保留全部差异行（唯一键去重），
 *       绝不后台改写人工处置结论。</li>
 * </ul>
 */
@Slf4j
@Service
public class ReconciliationService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(com.cloudmart.common.time.UserBusinessCalendar.DEFAULT_ZONE_ID);

    private final PaymentAttemptMapper attemptMapper;
    private final ReconciliationRunMapper runMapper;
    private final ReconciliationDifferenceMapper differenceMapper;
    private final OrderFeignClient orderFeignClient;
    private final com.cloudmart.payment.repository.RefundOrderMapper refundOrderMapper;
    private final com.cloudmart.payment.feign.InventoryReconFeignClient inventoryReconFeignClient;
    private final int batchSize;
    private final int leaseSeconds;
    /** 租约持有者标识：pid@host（日志可追踪接管方） */
    private final String instanceId;
    /** T20：统一 UTC 时钟（租约/扫描窗口/完成时间不依赖 JVM 默认时区） */
    private final Clock clock;

    public ReconciliationService(PaymentAttemptMapper attemptMapper,
                                 ReconciliationRunMapper runMapper,
                                 ReconciliationDifferenceMapper differenceMapper,
                                 OrderFeignClient orderFeignClient,
                                 com.cloudmart.payment.repository.RefundOrderMapper refundOrderMapper,
                                 com.cloudmart.payment.feign.InventoryReconFeignClient inventoryReconFeignClient,
                                 @Value("${ops.reconciliation.batch-size:200}") int batchSize,
                                 @Value("${ops.reconciliation.lease-seconds:300}") int leaseSeconds,
                                 Clock clock) {
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
        this.differenceMapper = differenceMapper;
        this.orderFeignClient = orderFeignClient;
        this.refundOrderMapper = refundOrderMapper;
        this.inventoryReconFeignClient = inventoryReconFeignClient;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.instanceId = ManagementFactory.getRuntimeMXBean().getName();
        this.clock = clock;
    }

    /** 依赖服务整页/整批不可达（区别于单笔不可核验）：保留断点转 PARTIAL，等待同日续扫 */
    static final class DependencyUnreachableException extends RuntimeException {
        DependencyUnreachableException(String message) {
            super(message);
        }

        DependencyUnreachableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** FAILED/PARTIAL 重试或失联接管：execute=false 时 run 为应直接返回的既有运行 */
    private record RunLease(ReconciliationRun run, boolean execute) {}

    /**
     * 幂等进入对账运行：
     * DONE → 幂等返回；RUNNING 租约有效 → 幂等返回；RUNNING 租约过期 → CAS 接管（续跑）；
     * PARTIAL/FAILED → CAS 认领续跑（保留统计/游标/差异）；并发认领失败 → 幂等返回。
     */
    private RunLease beginRun(LocalDate businessDate, String scope, String logTag) {
        ReconciliationRun existing = runMapper.findByDateAndScope(businessDate, scope);
        if (existing != null) {
            switch (existing.getStatus()) {
                case "DONE" -> {
                    log.info("[{}] 本业务日 {} 对账已完成（run={}），幂等返回", logTag, scope, existing.getId());
                    return new RunLease(existing, false);
                }
                case "RUNNING" -> {
                    if (existing.getLeaseUntil() != null && existing.getLeaseUntil().isAfter(LocalDateTime.now(clock))) {
                        log.info("[{}] 本业务日 {} 对账正在执行（run={} owner={} 租约有效），幂等返回",
                                logTag, scope, existing.getId(), existing.getLeaseOwner());
                        return new RunLease(existing, false);
                    }
                    if (runMapper.takeoverStaleRun(existing.getId(), instanceId, leaseSeconds) == 0) {
                        log.info("[{}] 失联运行 run={} 已被并发接管，幂等返回", logTag, existing.getId());
                        return new RunLease(runMapper.selectById(existing.getId()), false);
                    }
                    log.warn("[{}] 接管失联对账运行 run={}（owner={}），从断点续扫",
                            logTag, existing.getId(), existing.getLeaseOwner());
                    return new RunLease(runMapper.selectById(existing.getId()), true);
                }
                case "PARTIAL", "FAILED" -> {
                    if (runMapper.claimResumableRun(existing.getId(), instanceId, leaseSeconds) == 0) {
                        log.info("[{}] 可恢复运行 run={} 已被并发认领，幂等返回", logTag, existing.getId());
                        return new RunLease(runMapper.selectById(existing.getId()), false);
                    }
                    log.warn("[{}] 认领{}对账运行 run={}，从断点续扫（统计/差异保留）",
                            logTag, existing.getStatus(), scope, existing.getId());
                    return new RunLease(runMapper.selectById(existing.getId()), true);
                }
                default -> {
                    log.error("[{}] 运行 run={} 状态未知 {}，按可恢复运行 CAS 认领",
                            logTag, existing.getId(), existing.getStatus());
                    if (runMapper.claimResumableRun(existing.getId(), instanceId, leaseSeconds) == 0) {
                        return new RunLease(runMapper.selectById(existing.getId()), false);
                    }
                    return new RunLease(runMapper.selectById(existing.getId()), true);
                }
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ReconciliationRun run = new ReconciliationRun();
        run.setBusinessDate(businessDate);
        run.setScope(scope);
        run.setStatus("RUNNING");
        run.setTotalChecked(0);
        run.setTotalDiff(0);
        run.setTotalSkipped(0);
        run.setPaymentCursor(0L);
        run.setRefundCursor(0L);
        run.setInventoryCursor(0L);
        run.setOrderPage(0);
        run.setLeaseOwner(instanceId);
        run.setLeaseUntil(now.plusSeconds(leaseSeconds));
        run.setHeartbeatAt(now);
        // 时间统一取应用时钟：DB 默认值是 UTC 而应用是东八区，混用会导致
        // 同一行 started_at 与 finished_at 相差 8 小时（线上 run3 实测）
        run.setStartedAt(now);
        run.setCreatedAt(now);
        runMapper.insert(run);
        return new RunLease(run, true);
    }

    /** 逐批心跳：续租约并持久化断点游标与累计计数（崩溃后最多丢失当前批的进度） */
    private void persistProgress(ReconciliationRun run) {
        LocalDateTime now = LocalDateTime.now(clock);
        run.setHeartbeatAt(now);
        run.setLeaseUntil(now.plusSeconds(leaseSeconds));
        runMapper.updateById(run);
    }

    /** 安全读取订单状态：null/失败信封/抛异常一律视为不可核验（由调用方计 skipped） */
    private String queryOrderStatusQuietly(Long orderId, String logTag, Object bizRef) {
        try {
            ApiResponse<OrderInternalInfoDTO> orderResp = orderFeignClient.getOrderInfo(orderId);
            if (orderResp != null && orderResp.success() && orderResp.data() != null) {
                return orderResp.data().status();
            }
            log.warn("[{}] 订单状态查询返回不可核验信封 orderId={} ref={}", logTag, orderId, bizRef);
            return "UNREACHABLE";
        } catch (Exception queryError) {
            log.warn("[{}] 订单状态查询失败，跳过该条 orderId={} ref={}: {}",
                    logTag, orderId, bizRef, queryError.getMessage());
            return "UNREACHABLE";
        }
    }

    /**
     * 反向扫描专用：分页拉取已支付订单。失败信封/抛异常是"端点不可达"而非"真空页"——
     * 必须转 PARTIAL 保留断点，绝不能 break 后误标 DONE（R26 核心修复）。
     */
    private OrderFeignClient.PageDTO fetchPaidOrdersPage(int page, String logTag) {
        ApiResponse<OrderFeignClient.PageDTO> resp;
        try {
            resp = orderFeignClient.listPaidOrders(page, batchSize);
        } catch (Exception e) {
            throw new DependencyUnreachableException(
                    "[" + logTag + "] 反向扫描分页拉取失败 page=" + page + "（依赖不可达，保留断点）", e);
        }
        if (resp == null || !resp.success() || resp.data() == null) {
            throw new DependencyUnreachableException(
                    "[" + logTag + "] 反向扫描分页返回失败信封/空数据 page=" + page
                            + "（区别于真空页，保留断点）");
        }
        return resp.data();
    }

    /** 执行一次支付↔订单对账（扫描最近 N 天 SUCCESS 支付核对订单状态）。 */
    public ReconciliationRun runPaymentOrderReconciliation(int scanDays) {
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        RunLease lease = beginRun(businessDate, "PAYMENT_ORDER", "OPS01");
        if (!lease.execute()) {
            return lease.run();
        }
        ReconciliationRun run = lease.run();

        try {
            int checked = nz(run.getTotalChecked());
            int skipped = nz(run.getTotalSkipped());
            int diffs = nz(run.getTotalDiff());
            LocalDateTime since = LocalDateTime.now(clock).minusDays(scanDays);

            // 核对一：SUCCESS 支付的订单必须已 PAID+（id 游标断点续扫）
            long lastId = nz(run.getPaymentCursor());
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
                    String orderStatus = queryOrderStatusQuietly(attempt.getOrderId(), "OPS01",
                            "attemptId=" + attempt.getId());
                    if ("UNREACHABLE".equals(orderStatus)) {
                        // T11：依赖服务不可达计 skipped 不计 checked——差异必须两侧可核验
                        skipped++;
                        continue;
                    }
                    checked++;
                    if (isPaidOrBeyond(orderStatus)) {
                        continue;
                    }
                    diffs += recordDiff(run.getId(), "PAYMENT_SUCCESS_ORDER_NOT_PAID",
                            String.valueOf(attempt.getId()), "HIGH",
                            "支付 SUCCESS 但订单状态: " + orderStatus,
                            "{\"paymentStatus\":\"SUCCESS\",\"orderStatus\":\"" + orderStatus
                                    + "\",\"orderId\":" + attempt.getOrderId() + "}");
                }
                run.setPaymentCursor(lastId);
                run.setTotalChecked(checked);
                run.setTotalSkipped(skipped);
                run.setTotalDiff(diffs);
                persistProgress(run);
                if (batch.size() < batchSize) {
                    break;
                }
            }

            // 核对二：PAID+ 订单必须有 SUCCESS 支付记录（断点页码续扫；页失败转 PARTIAL）
            int page = nz(run.getOrderPage()) + 1;
            while (true) {
                OrderFeignClient.PageDTO paidPage = fetchPaidOrdersPage(page, "OPS01");
                List<OrderInternalInfoDTO> records = paidPage.records();
                if (records.isEmpty()) {
                    break;
                }
                for (OrderInternalInfoDTO order : records) {
                    checked++;
                    // R11：零元单凭证是订单域 ZERO_PAY 内部结算事实（无渠道支付单），
                    // 不应报"无 SUCCESS 支付凭证"
                    if (order.zeroPay()) {
                        continue;
                    }
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
                run.setOrderPage(page);
                run.setTotalChecked(checked);
                run.setTotalDiff(diffs);
                persistProgress(run);
                if (records.size() < batchSize) {
                    break;
                }
                page++;
            }

            return finishDone(run, checked, diffs, skipped, "OPS01");
        } catch (DependencyUnreachableException e) {
            return finishPartial(run, "OPS01", e);
        } catch (Exception e) {
            return finishFailed(run, "OPS01", e);
        }
    }

    /**
     * T11：退款层对账—— refund_order 台账（SUCCEEDED）与订单状态双向核对：
     *   ① 退款单 SUCCEEDED → 订单必须 REFUNDED（未推进=事件丢失，HIGH）；
     *   ② 订单 REFUNDED → 必须存在 SUCCEEDED 退款单（无台账=资金事实缺失，HIGH）。
     */
    public ReconciliationRun runRefundReconciliation(int scanDays) {
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        RunLease lease = beginRun(businessDate, "REFUND", "T11");
        if (!lease.execute()) {
            return lease.run();
        }
        ReconciliationRun run = lease.run();

        try {
            int checked = nz(run.getTotalChecked());
            int skipped = nz(run.getTotalSkipped());
            int diffs = nz(run.getTotalDiff());
            LocalDateTime since = LocalDateTime.now(clock).minusDays(scanDays);

            // ① 退款单 SUCCEEDED → 订单 REFUNDED
            long lastId = nz(run.getRefundCursor());
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
                    String orderStatus = queryOrderStatusQuietly(refund.getOrderId(), "T11",
                            "refundNo=" + refund.getRefundNo());
                    if ("UNREACHABLE".equals(orderStatus)) {
                        skipped++;
                        continue;
                    }
                    checked++;
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
                run.setRefundCursor(lastId);
                run.setTotalChecked(checked);
                run.setTotalSkipped(skipped);
                run.setTotalDiff(diffs);
                persistProgress(run);
                if (batch.size() < batchSize) {
                    break;
                }
            }

            // ② REFUNDED 订单 → 必须有 SUCCEEDED 退款单
            int page = nz(run.getOrderPage()) + 1;
            while (true) {
                OrderFeignClient.PageDTO paidPage = fetchPaidOrdersPage(page, "T11");
                List<OrderInternalInfoDTO> records = paidPage.records();
                if (records.isEmpty()) {
                    break;
                }
                for (OrderInternalInfoDTO order : records) {
                    if (!"REFUNDED".equals(order.status())) {
                        continue;
                    }
                    checked++;
                    Long succeeded = refundOrderMapper.selectCount(
                            new LambdaQueryWrapper<com.cloudmart.payment.entity.RefundOrder>()
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
                run.setOrderPage(page);
                run.setTotalChecked(checked);
                run.setTotalDiff(diffs);
                persistProgress(run);
                if (records.size() < batchSize) {
                    break;
                }
                page++;
            }

            return finishDone(run, checked, diffs, skipped, "T11");
        } catch (DependencyUnreachableException e) {
            return finishPartial(run, "T11", e);
        } catch (Exception e) {
            return finishFailed(run, "T11", e);
        }
    }

    /**
     * T11：库存层对账——预占台账与订单状态双向核对：
     *   ① 台账 RESERVED 且预占创建超 24h 但订单已关闭（滞留=事件丢失，HIGH）；
     *   ② 台账 CONFIRMED 但订单 CANCELLED/REFUNDED（资金货两失风险，HIGH）。
     * 台账端点不可达 → PARTIAL（断点保留），不再静默 0。
     */
    public ReconciliationRun runInventoryReconciliation(int scanDays) {
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        RunLease lease = beginRun(businessDate, "INVENTORY", "T11");
        if (!lease.execute()) {
            return lease.run();
        }
        ReconciliationRun run = lease.run();

        try {
            int checked = nz(run.getTotalChecked());
            int skipped = nz(run.getTotalSkipped());
            int diffs = nz(run.getTotalDiff());
            LocalDateTime since = LocalDateTime.now(clock).minusDays(scanDays);
            long lastId = nz(run.getInventoryCursor());
            while (true) {
                // "扫不到"与"没有可核对的行"必须区分：端点失败转 PARTIAL，不按空批顺延
                ApiResponse<java.util.List<com.cloudmart.payment.dto.ReservationScanDTO>> scanResp;
                try {
                    scanResp = inventoryReconFeignClient.scanReservations(since, lastId, batchSize);
                } catch (Exception e) {
                    throw new DependencyUnreachableException("[T11] 库存台账扫描请求异常", e);
                }
                if (scanResp == null || !scanResp.success() || scanResp.data() == null) {
                    throw new DependencyUnreachableException("[T11] 库存台账扫描返回失败信封: "
                            + (scanResp == null || scanResp.error() == null ? "null"
                            : scanResp.error().code() + " " + scanResp.error().message()));
                }
                java.util.List<com.cloudmart.payment.dto.ReservationScanDTO> batch = scanResp.data();
                if (batch.isEmpty()) {
                    break;
                }
                for (com.cloudmart.payment.dto.ReservationScanDTO row : batch) {
                    lastId = Math.max(lastId, row.orderId() == null ? lastId : row.orderId());
                    String orderStatus = queryOrderStatusQuietly(row.orderId(), "T11",
                            "reservation orderId=" + row.orderId());
                    if ("UNREACHABLE".equals(orderStatus)) {
                        skipped++;
                        continue;
                    }
                    checked++;
                    if ("RESERVED".equals(row.status()) && !row.createdAt().isBefore(LocalDateTime.now(clock).minusHours(24))) {
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
                    if ("CONFIRMED".equals(row.status()) && orderGone) {
                        diffs += recordDiff(run.getId(), "RESERVATION_CONFIRMED_ORDER_CANCELLED",
                                String.valueOf(row.orderId()), "HIGH",
                                "台账 CONFIRMED 但订单 " + orderStatus + "（资金货两失风险）",
                                "{\"reservationStatus\":\"CONFIRMED\",\"orderStatus\":\"" + orderStatus
                                        + "\",\"orderId\":" + row.orderId() + "}");
                    }
                }
                run.setInventoryCursor(lastId);
                run.setTotalChecked(checked);
                run.setTotalSkipped(skipped);
                run.setTotalDiff(diffs);
                persistProgress(run);
                if (batch.size() < batchSize) {
                    break;
                }
            }

            return finishDone(run, checked, diffs, skipped, "T11-INV");
        } catch (DependencyUnreachableException e) {
            return finishPartial(run, "T11-INV", e);
        } catch (Exception e) {
            return finishFailed(run, "T11-INV", e);
        }
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }

    private ReconciliationRun finishDone(ReconciliationRun run, int checked, int diffs, int skipped, String tag) {
        run.setTotalChecked(checked);
        run.setTotalDiff(diffs);
        run.setTotalSkipped(skipped);
        run.setStatus("DONE");
        run.setFinishedAt(LocalDateTime.now(clock));
        persistProgress(run);
        log.info("[{}] 对账完成 run={} checked={} diffs={} skipped={}", tag, run.getId(), checked, diffs, skipped);
        return run;
    }

    /** 依赖不可达：PARTIAL 保留断点（finishedAt 留空），同日下次触发从游标续扫 */
    private ReconciliationRun finishPartial(ReconciliationRun run, String tag, DependencyUnreachableException e) {
        run.setStatus("PARTIAL");
        run.setFinishedAt(null);
        persistProgress(run);
        log.warn("[{}] 对账依赖不可达 run={} 转 PARTIAL，断点保留待同日续扫: paymentCursor={} refundCursor={} "
                        + "inventoryCursor={} orderPage={} checked={} skipped={} reason={}",
                tag, run.getId(), run.getPaymentCursor(), run.getRefundCursor(),
                run.getInventoryCursor(), run.getOrderPage(), run.getTotalChecked(), run.getTotalSkipped(),
                e.getMessage());
        return run;
    }

    private ReconciliationRun finishFailed(ReconciliationRun run, String tag, Exception e) {
        run.setStatus("FAILED");
        run.setFinishedAt(LocalDateTime.now(clock));
        runMapper.updateById(run);
        log.error("[{}] 对账执行失败 run={}（可从断点重试）", tag, run.getId(), e);
        throw new IllegalStateException("对账执行失败 run=" + run.getId() + ": " + e.getMessage(), e);
    }

    private boolean isPaidOrBeyond(String orderStatus) {
        return "PAID".equals(orderStatus) || "SHIPPED".equals(orderStatus)
                || "COMPLETED".equals(orderStatus) || "REFUNDING".equals(orderStatus)
                || "REFUNDED".equals(orderStatus);
    }

    /** 差异唯一（run+type+biz_id），重复对账/断点续扫不产生重复差异，人工处置证据原样保留。 */
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
