package com.cloudmart.payment.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * OPS-01/R26：定时对账——每日低峰全量核对；另设恢复节拍接管当日失联（租约过期的
 * RUNNING）与 PARTIAL/FAILED 运行，从断点续扫（重复调度不双跑、DONE 幂等）。
 * 差异落账本供工作台处置，不做任何后台自动"勾销"。
 */
@Slf4j
@Component
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;
    private final boolean enabled;
    private final int scanDays;

    public ReconciliationScheduler(ReconciliationService reconciliationService,
                                   @Value("${ops.reconciliation.schedule-enabled:true}") boolean enabled,
                                   @Value("${ops.reconciliation.scan-days:7}") int scanDays) {
        this.reconciliationService = reconciliationService;
        this.enabled = enabled;
        this.scanDays = scanDays;
    }

    @Scheduled(cron = "${ops.reconciliation.cron:0 30 2 * * *}")
    public void dailyReconciliation() {
        if (!enabled) {
            return;
        }
        try {
            reconciliationService.runPaymentOrderReconciliation(scanDays);
        } catch (Exception e) {
            // 失败不影响下次调度；运行行已落 FAILED/PARTIAL，恢复节拍/管理端可续跑
            log.error("[OPS01] 定时对账执行失败: {}", e.getMessage());
        }
    }

    /**
     * R26：周期恢复扫描——当日 RUNNING 租约过期（进程 kill/崩溃）或
     * PARTIAL（依赖短时不可达）自动从断点接管续扫；活跃/DONE 运行幂等跳过。
     */
    @Scheduled(fixedDelayString = "${ops.reconciliation.recover-delay-ms:1800000}",
               initialDelayString = "${ops.reconciliation.recover-initial-delay-ms:120000}")
    public void recoverInterruptedRuns() {
        if (!enabled) {
            return;
        }
        recoverOne("PAYMENT_ORDER", () -> reconciliationService.runPaymentOrderReconciliation(scanDays));
        recoverOne("REFUND", () -> reconciliationService.runRefundReconciliation(scanDays));
        recoverOne("INVENTORY", () -> reconciliationService.runInventoryReconciliation(scanDays));
    }

    private void recoverOne(String scope, Supplier<ReconciliationRun> invocation) {
        try {
            ReconciliationRun run = invocation.get();
            if (run != null && "RUNNING".equals(run.getStatus()) && run.getHeartbeatAt() != null
                    && run.getTotalChecked() != null && run.getTotalChecked() > 0) {
                log.info("[R26] {} 对账运行从断点接管续跑 run={}", scope, run.getId());
            }
        } catch (Exception e) {
            log.warn("[R26] {} 对账恢复尝试失败（等待下一节拍）: {}", scope, e.getMessage());
        }
    }
}
