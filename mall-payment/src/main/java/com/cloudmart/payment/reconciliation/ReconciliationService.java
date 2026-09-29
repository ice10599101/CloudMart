package com.cloudmart.payment.reconciliation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.entity.Payment;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对账服务（OPS-01）：支付 ↔ 订单四方一致性的持久化核对。
 *
 * <p>核对规则（首期覆盖资金最高风险面）：</p>
 * <ul>
 *   <li>PAYMENT_SUCCESS_ORDER_NOT_PAID：支付 SUCCESS 但订单未 PAID——资金已收、
 *       履约未启动，HIGH；</li>
 *   <li>ORDER_PAID_NO_SUCCESS_PAYMENT：订单 PAID 但无 SUCCESS 支付记录——
 *       状态推进无资金凭证，HIGH。</li>
 * </ul>
 *
 * <p>处置语义（方案 §8.4）：人工解决不直接改资金，只登记证据/触发受控流程。
 * 差异按 (run, diff_type, biz_id) 唯一——重复对账不产生重复差异。</p>
 */
@Slf4j
@Service
public class ReconciliationService {

    private final PaymentMapper paymentMapper;
    private final ReconciliationRunMapper runMapper;
    private final ReconciliationDifferenceMapper differenceMapper;
    private final OrderFeignClient orderFeignClient;
    private final int batchSize;

    public ReconciliationService(PaymentMapper paymentMapper,
                                 ReconciliationRunMapper runMapper,
                                 ReconciliationDifferenceMapper differenceMapper,
                                 OrderFeignClient orderFeignClient,
                                 @Value("${ops.reconciliation.batch-size:200}") int batchSize) {
        this.paymentMapper = paymentMapper;
        this.runMapper = runMapper;
        this.differenceMapper = differenceMapper;
        this.orderFeignClient = orderFeignClient;
        this.batchSize = batchSize;
    }

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

            // 核对一：SUCCESS 支付的订单必须已 PAID+（状态经订单服务权威查询）
            var payments = paymentMapper.selectList(new QueryWrapper<Payment>()
                    .eq("status", "SUCCESS")
                    .ge("created_at", since)
                    .last("LIMIT " + batchSize));
            for (Payment payment : payments) {
                checked++;
                String orderStatus;
                try {
                    ApiResponse<OrderInternalInfoDTO> orderResp =
                            orderFeignClient.getOrderInfo(payment.getOrderId());
                    orderStatus = orderResp != null && orderResp.success() && orderResp.data() != null
                            ? orderResp.data().status() : "UNREACHABLE";
                } catch (Exception queryError) {
                    // 订单服务不可达：该条跳过不误报（差异必须两侧状态可核验才成立）
                    log.warn("[OPS01] 订单状态查询失败，跳过该条 paymentId={}: {}",
                            payment.getId(), queryError.getMessage());
                    continue;
                }
                if (isPaidOrBeyond(orderStatus)) {
                    continue;
                }
                diffs += recordDiff(run.getId(), "PAYMENT_SUCCESS_ORDER_NOT_PAID",
                        String.valueOf(payment.getId()), "HIGH",
                        "支付 SUCCESS 但订单状态: " + orderStatus,
                        "{\"paymentStatus\":\"SUCCESS\",\"orderStatus\":\"" + orderStatus
                                + "\",\"orderId\":" + payment.getOrderId() + "}");
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
                    Long count = paymentMapper.selectCount(new QueryWrapper<Payment>()
                            .eq("order_id", order.orderId())
                            .eq("status", "SUCCESS"));
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
        log.warn("[OPS01] 对账差异 type={} bizId={} detail={}", type, bizId, detail);
        return 1;
    }
}
