package com.cloudmart.payment.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OPS-01：定时对账——每日低峰自动执行支付↔订单核对（可配置关闭，
 * 管理端仍可手动触发）。差异落账本供工作台处置。
 */
@Slf4j
@Component
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;
    private final boolean enabled;

    public ReconciliationScheduler(ReconciliationService reconciliationService,
                                   @Value("${ops.reconciliation.schedule-enabled:true}") boolean enabled) {
        this.reconciliationService = reconciliationService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${ops.reconciliation.cron:0 30 2 * * *}")
    public void dailyReconciliation() {
        if (!enabled) {
            return;
        }
        try {
            reconciliationService.runPaymentOrderReconciliation(7);
        } catch (Exception e) {
            // 失败不影响下次调度；管理端可手动触发并查看 FAILED 运行
            log.error("[OPS01] 定时对账执行失败（下次调度重试）: {}", e.getMessage());
        }
    }
}
