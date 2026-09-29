package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletLedger;
import com.cloudmart.pet.entity.PetWalletReconcileItem;
import com.cloudmart.pet.entity.PetWalletReconcileRun;
import com.cloudmart.pet.config.PetMetrics;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletLedgerMapper;
import com.cloudmart.pet.repository.PetWalletReconcileItemMapper;
import com.cloudmart.pet.repository.PetWalletReconcileRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 钱包对账任务（W04/§5.5）。
 *
 * <p>对每个账户在固定 account_version 上界（防并发新单造成假差异）检查：</p>
 * <pre>
 *   余额 = 期初余额(0) + SUM(全部已提交流水.delta)
 *   账本相邻版本链式一致（balance_after = 下一账本 balance_before）由 DDL CHECK 与写入路径保证，
 *   此处校验"当前余额 == 该版本上界内 SUM(delta) + 期初(0)"这一主不变量。
 * </pre>
 *
 * <p>差异落 {@code pet_wallet_reconcile_item}（OPEN，人工处置）+ 计数指标；
 * <b>不自动用当前余额覆盖流水</b>（§5.5）。资产差异>0 属立即阻断项，由值班按运维手册处置
 * （必要时经发布流程切 PAUSED 暂停新收支）。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PetWalletReconcileJob {

    /** 单轮扫描账户上限（大库按分片推进；首轮基线覆盖全量小库足够） */
    private static final int MAX_ACCOUNTS_PER_RUN = 5000;

    private final PetWalletAccountMapper accountMapper;
    private final PetWalletLedgerMapper ledgerMapper;
    private final PetWalletReconcileRunMapper runMapper;
    private final PetWalletReconcileItemMapper itemMapper;
    private final PetMetrics metrics;
    private final com.cloudmart.pet.mq.PetEventProducer eventProducer;
    private final com.cloudmart.pet.config.PetProperties properties;

    /** 每日全量对账（凌晨 2:30，避开业务高峰；分钟级抽查由指标侧覆盖） */
    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    public void reconcileDaily() {
        reconcile("SCHEDULED");
    }

    /** 可编程触发（运维/测试用） */
    public void reconcile(String trigger) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC);
        PetWalletReconcileRun run = new PetWalletReconcileRun();
        run.setCutoff(cutoff);
        run.setStatus("RUNNING");
        run.setAccountCount(0);
        run.setDiffCount(0);
        runMapper.insert(run);

        try {
            List<PetWalletAccount> accounts = accountMapper.selectList(
                    new LambdaQueryWrapper<PetWalletAccount>()
                            .orderByAsc(PetWalletAccount::getId)
                            .last("LIMIT " + MAX_ACCOUNTS_PER_RUN));
            int diffCount = 0;
            for (PetWalletAccount account : accounts) {
                long expected = sumDeltasToVersion(account.getId(), account.getVersion());
                if (expected != account.getBalance()) {
                    diffCount++;
                    PetWalletReconcileItem item = new PetWalletReconcileItem();
                    item.setRunId(run.getId());
                    item.setAccountId(account.getId());
                    item.setExpectedBalance(expected);
                    item.setActualBalance(account.getBalance());
                    item.setLastVersion(account.getVersion());
                    item.setDiff(account.getBalance() - expected);
                    item.setStatus("OPEN");
                    itemMapper.insert(item);
                    // P2-5：逐条差异计数（区别于 run 级完成计数），供告警阈值/趋势监控
                    metrics.increment("pet_wallet_reconcile_diff", "trigger", trigger);
                    log.error("[RECONCILE DIFF] 账本不平! accountId={}, user={}, expected={}, actual={}, diff={}, version={}",
                            account.getId(), account.getUserId(), expected, account.getBalance(),
                            account.getBalance() - expected, account.getVersion());
                }
            }
            run.setAccountCount(accounts.size());
            run.setDiffCount(diffCount);
            run.setStatus("COMPLETED");
            run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            runMapper.updateById(run);
            metrics.increment("pet_wallet_reconcile_diff_count", "trigger", trigger);
            if (diffCount > 0) {
                // P2-5：告警经 outbox 可靠投递给管理员（站内信落库推送）；差异本身仍由值班人工处置，不自动改平
                String eventId = "PET_WALLET_RECONCILE_ALERT:" + run.getId();
                eventProducer.publishViaOutbox(com.cloudmart.pet.config.RocketMQConfig.PET_TAG_WALLET_ALERT,
                        new com.cloudmart.pet.mq.PetEventProducer.PetEventMessage(
                                eventId,
                                String.valueOf(properties.getAlert().getAdminUserId()),
                                "PET_WALLET_RECONCILE_ALERT",
                                "钱包对账差异告警",
                                "对账批次 " + run.getId() + " 发现 " + diffCount + " 个账本差异，"
                                        + "差异明细已进入 pet_wallet_reconcile_item 待处置队列，请立即核查。",
                                String.valueOf(run.getId()), "PET_WALLET_RECONCILE_ALERT"));
                log.error("[RECONCILE] 对账完成但存在差异, runId={}, accounts={}, diffs={}, 已发送管理员告警",
                        run.getId(), accounts.size(), diffCount);
            } else {
                log.info("[RECONCILE] 对账完成，全部平账, runId={}, accounts={}", run.getId(), accounts.size());
            }
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            runMapper.updateById(run);
            log.error("[RECONCILE] 对账批次失败, runId={}", run.getId(), e);
        }
    }

    /** 固定版本上界内的流水 delta 求和（防并发新单造成假差异） */
    private long sumDeltasToVersion(Long accountId, long versionUpperBound) {
        List<PetWalletLedger> ledgers = ledgerMapper.selectList(
                new LambdaQueryWrapper<PetWalletLedger>()
                        .eq(PetWalletLedger::getAccountId, accountId)
                        .le(PetWalletLedger::getAccountVersion, versionUpperBound)
                        .orderByAsc(PetWalletLedger::getAccountVersion));
        // 期初余额恒 0（§6.5-5：新开账户无 0 金额流水，期初由账户创建时间/切换批次记录）
        return ledgers.stream().mapToLong(PetWalletLedger::getDelta).sum();
    }
}
