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
 * 钱包对账任务（W04/§5.5/R19）。
 *
 * <p>对每个账户在固定 account_version 上界（防并发新单造成假差异）检查：</p>
 * <pre>
 *   余额 = 期初余额(0) + SUM(全部已提交流水.delta)
 *   账本相邻版本链式一致（balance_after = 下一账本 balance_before）由 DDL CHECK 与写入路径保证，
 *   此处校验"当前余额 == 该版本上界内 SUM(delta) + 期初(0)"这一主不变量。
 * </pre>
 *
 * <p>R19 修复：</p>
 * <ul>
 *   <li><b>全量覆盖</b>：keyset 分批（id &gt; cursor）推进到本轮创建时快照的 maxAccountId——
 *       原实现固定 LIMIT 5000 从头扫描，第 5001+ 账户永久不覆盖；</li>
 *   <li><b>可恢复游标</b>：游标随批持久化到 run 行——中断后（调度器续跑/手动重跑）
 *       从原游标继续，不重扫不漏扫；</li>
 *   <li><b>内存有界</b>：每账户 SUM 用 SQL 聚合（原实现把账户全部账本行装入 JVM，
 *       长流水账户 OOM 风险）；</li>
 *   <li><b>指标口径</b>：diff 指标按差异条数递增（原实现每轮固定 +1，掩盖差异规模）。</li>
 * </ul>
 *
 * <p>差异落 {@code pet_wallet_reconcile_item}（OPEN，人工处置）；
 * <b>不自动用当前余额覆盖流水</b>（§5.5）。资产差异&gt;0 属立即阻断项，由值班按运维手册处置
 * （必要时经发布流程切 PAUSED 暂停新收支）。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PetWalletReconcileJob {

    /** 每批账户数（批间持久化游标） */
    private static final int BATCH_SIZE = 500;
    /** 单轮最大批数（限时长；未完成部分下轮从游标续跑） */
    private static final int MAX_BATCHES_PER_ROUND = 20;

    private final PetWalletAccountMapper accountMapper;
    private final PetWalletReconcileRunMapper runMapper;
    private final PetWalletReconcileItemMapper itemMapper;
    private final PetMetrics metrics;
    private final com.cloudmart.pet.mq.PetEventProducer eventProducer;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 每日全量对账（凌晨 2:30，UTC；分钟级抽查由指标侧覆盖） */
    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    public void reconcileDaily() {
        reconcile("SCHEDULED");
    }

    /** 可编程触发（运维/测试用） */
    public void reconcile(String trigger) {
        // R19：存在未完成 run（RUNNING/FAILED 且游标未到上界）→ 续跑而非开新轮（不重扫不漏扫）
        PetWalletReconcileRun run = runMapper.selectOne(new LambdaQueryWrapper<PetWalletReconcileRun>()
                .in(PetWalletReconcileRun::getStatus, "RUNNING", "FAILED")
                .orderByAsc(PetWalletReconcileRun::getId)
                .last("LIMIT 1"));
        boolean resumed = run != null;
        if (resumed && "FAILED".equals(run.getStatus())) {
            run.setStatus("RUNNING");
            run.setFinishedAt(null);
            runMapper.updateById(run);
        }
        if (run == null) {
            run = new PetWalletReconcileRun();
            run.setCutoff(LocalDateTime.now(ZoneOffset.UTC));
            run.setStatus("RUNNING");
            run.setAccountCount(0);
            run.setDiffCount(0);
            Long maxId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) FROM pet_wallet_account", Long.class);
            run.setMaxAccountId(maxId != null ? maxId : 0L);
            run.setCursorAccountId(0L);
            runMapper.insert(run);
        }
        try {
            reconcileRun(run, trigger, resumed);
        } catch (Exception e) {
            // 游标已随批持久化：run 保持 RUNNING，下轮续跑
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            runMapper.updateById(run);
            log.error("[RECONCILE] 对账批次失败（游标已持久化，可续跑）, runId={}, cursor={}",
                    run.getId(), run.getCursorAccountId(), e);
        }
    }

    /** keyset 分批推进到 maxAccountId；批间持久化游标 */
    private void reconcileRun(PetWalletReconcileRun run, String trigger, boolean resumed) {
        long maxAccountId = run.getMaxAccountId() != null ? run.getMaxAccountId() : 0L;
        long cursor = run.getCursorAccountId() != null ? run.getCursorAccountId() : 0L;
        int diffCount = run.getDiffCount() != null ? run.getDiffCount() : 0;
        int scanned = run.getAccountCount() != null ? run.getAccountCount() : 0;
        if (resumed) {
            log.info("[RECONCILE] 续跑未完成对账, runId={}, cursor={}, max={}", run.getId(), cursor, maxAccountId);
        }
        int batches = 0;
        while (cursor < maxAccountId && batches < MAX_BATCHES_PER_ROUND) {
            List<PetWalletAccount> accounts = accountMapper.selectList(
                    new LambdaQueryWrapper<PetWalletAccount>()
                            .gt(PetWalletAccount::getId, cursor)
                            .le(PetWalletAccount::getId, maxAccountId)
                            .orderByAsc(PetWalletAccount::getId)
                            .last("LIMIT " + BATCH_SIZE));
            if (accounts.isEmpty()) {
                break;
            }
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
                    try {
                        itemMapper.insert(item);
                    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                        // 续跑重扫同账户：差异项已存在（uk runId+accountId），不重复建单
                    }
                    // R19：差异指标按条数递增（告警阈值/趋势可反映差异规模）
                    metrics.increment("pet_wallet_reconcile_diff", "trigger", trigger);
                    log.error("[RECONCILE DIFF] 账本不平! accountId={}, user={}, expected={}, actual={}, diff={}, version={}",
                            account.getId(), account.getUserId(), expected, account.getBalance(),
                            account.getBalance() - expected, account.getVersion());
                }
            }
            scanned += accounts.size();
            cursor = accounts.get(accounts.size() - 1).getId();
            batches++;
            // 游标随批持久化：中断后从原游标续跑
            run.setAccountCount(scanned);
            run.setDiffCount(diffCount);
            run.setCursorAccountId(cursor);
            runMapper.updateById(run);
        }
        if (cursor >= maxAccountId) {
            run.setAccountCount(scanned);
            run.setDiffCount(diffCount);
            run.setCursorAccountId(cursor);
            run.setStatus("COMPLETED");
            run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            runMapper.updateById(run);
            if (diffCount > 0) {
                // 告警经 outbox 可靠投递给管理员；差异本身仍由值班人工处置，不自动改平
                String eventId = "PET_WALLET_RECONCILE_ALERT:" + run.getId();
                eventProducer.publishViaOutbox(com.cloudmart.pet.config.RocketMQConfig.PET_TAG_WALLET_ALERT,
                        new com.cloudmart.pet.mq.PetEventProducer.PetEventMessage(
                                eventId,
                                String.valueOf(properties.getAlert().getAdminUserId()),
                                "PET_WALLET_RECONCILE_ALERT",
                                "钱包对账差异告警",
                                "对账批次 " + run.getId() + " 发现 " + diffCount + " 个账本差异，"
                                        + "差异明细已进入 pet_wallet_reconcile_item 待处置队列，请立即核查。",
                                String.valueOf(run.getId()), "PET_WALLET_RECONCILE_ALERT"),
                        null);
                log.error("[RECONCILE] 对账完成但存在差异, runId={}, accounts={}, diffs={}, 已发送管理员告警",
                        run.getId(), scanned, diffCount);
            } else {
                log.info("[RECONCILE] 对账完成，全部平账, runId={}, accounts={}", run.getId(), scanned);
            }
        } else {
            log.info("[RECONCILE] 本轮时长/批数上限 reached，游标已持久化下轮续跑, runId={}, cursor={}, max={}",
                    run.getId(), cursor, maxAccountId);
        }
    }

    /** 固定版本上界内的流水 delta 求和（SQL 聚合——内存有界，长流水账户不再全量装载） */
    private long sumDeltasToVersion(Long accountId, long versionUpperBound) {
        Long sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(delta), 0) FROM pet_wallet_ledger "
                        + "WHERE account_id = ? AND account_version <= ?",
                Long.class, accountId, versionUpperBound);
        return sum != null ? sum : 0L;
    }
    /**
     * §8.2 差异人工处置：记录调查结论/关联补偿单并置 RESOLVED——只更新差异行，
     * 不改账本不改余额（余额修复只能经调账补偿走正常审批链）。
     * CAS（OPEN → RESOLVED）：并发重复处置只有一方生效，其余返回原状态。
     *
     * @return 处置后的差异行
     */
    @org.springframework.transaction.annotation.Transactional
    public PetWalletReconcileItem resolveDiff(Long itemId, Long operatorAdminId,
                                              String note, String resolutionRef) {
        if (operatorAdminId == null || operatorAdminId <= 0) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "管理员身份缺失");
        }
        if (note == null || note.isBlank()) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "处置结论必填");
        }
        PetWalletReconcileItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "差异记录不存在");
        }
        String resolvedNote = note.strip() + (resolutionRef != null && !resolutionRef.isBlank()
                ? "（关联单号: " + resolutionRef.strip() + "）" : "");
        int updated = itemMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetWalletReconcileItem>()
                .set(PetWalletReconcileItem::getStatus, "RESOLVED")
                .set(PetWalletReconcileItem::getResolutionNote, resolvedNote.length() > 500
                        ? resolvedNote.substring(0, 500) : resolvedNote)
                .set(PetWalletReconcileItem::getResolvedBy, operatorAdminId)
                .set(PetWalletReconcileItem::getResolvedAt, LocalDateTime.now(ZoneOffset.UTC))
                .eq(PetWalletReconcileItem::getId, itemId)
                .eq(PetWalletReconcileItem::getStatus, "OPEN"));
        if (updated == 0) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_STATE_CONFLICT, "差异已被处置，请刷新后重试");
        }
        log.info("对账差异人工处置完成, itemId={}, diff={}, operator={}", itemId, item.getDiff(), operatorAdminId);
        return itemMapper.selectById(itemId);
    }
}
