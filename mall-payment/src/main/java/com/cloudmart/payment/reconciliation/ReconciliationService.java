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
                                 @Value("${ops.reconciliation.batch-size:200}") int batchSize) {
        this.attemptMapper = attemptMapper;
        this.runMapper = runMapper;
        this.differenceMapper = differenceMapper;
        this.orderFeignClient = orderFeignClient;
        this.refundOrderMapper = refundOrderMapper;
        this.batchSize = batchSize;
    }

    private final com.cloudmart.payment.repository.RefundOrderMapper refundOrderMapper;

    /** 执行一次支付↔订单对账（扫描最近 N 天 SUCCESS 支付核对订单状态）。 */
    public ReconciliationRun runPaymentOrderReconciliation(int scanDays) {
        LocalDate businessDate = LocalDate.now();
        ReconciliationRun run = new ReconciliationRun();
        run.setBusinessDate(businessDate);
        run.setScope("PAYMENT_ORDER");
        run.setStatus("RUNNING");
        runMapper.insert(run);

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
        ReconciliationRun run = new ReconciliationRun();
        run.setBusinessDate(businessDate);
        run.setScope("REFUND");
        run.setStatus("RUNNING");
        runMapper.insert(run);

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
