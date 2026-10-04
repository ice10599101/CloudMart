package com.cloudmart.pet.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.repository.PetOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * B22/P2-5 数据保留策略：SENT outbox 事件保留 30 天后清理；
 * 钱包账本（pet_wallet_ledger）按月归档汇总——只写不删：物理清理涉及对账基线联动改造
 * （PetWalletReconcileJob 以全量 SUM(delta) 为基线），删除策略需产品确认后另行启用。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PetDataRetentionScheduler {

    /** 账本归档水位：只归档早于该天数的流水（UTC） */
    private static final int LEDGER_ARCHIVE_AFTER_DAYS = 180;

    private final PetOutboxEventMapper outboxMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    public void purgeSentOutbox() {
        int deleted = outboxMapper.delete(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetOutboxEvent>()
                .eq(com.cloudmart.pet.entity.PetOutboxEvent::getStatus, "SENT")
                .lt(com.cloudmart.pet.entity.PetOutboxEvent::getUpdatedAt,
                        LocalDateTime.now(ZoneOffset.UTC).minusDays(30)));
        if (deleted > 0) {
            log.info("outbox 已发送事件清理: {} rows", deleted);
        }
    }

    /** 账本月度归档（P2-5）：180 天前的流水按 (account, 月份) 聚合 upsert 到归档表，原流水保留 */
    @Scheduled(cron = "0 10 4 * * *", zone = "UTC")
    public void archiveWalletLedger() {
        try {
            // id 取组内 MAX(id)（组间不相交，全局唯一）；期末余额经 ending_version 精确回查，
            // 避免 GROUP_CONCAT 截断导致取错值
            int written = jdbcTemplate.update("""
                    INSERT INTO pet_wallet_ledger_archive
                        (id, account_id, stat_month, entry_count, sum_delta, ending_version, ending_balance_after)
                    SELECT g.max_id, g.account_id, g.stat_month, g.entry_count, g.sum_delta, g.ending_version,
                           (SELECT l2.balance_after FROM pet_wallet_ledger l2
                            WHERE l2.account_id = g.account_id AND l2.account_version = g.ending_version
                            LIMIT 1)
                    FROM (
                        SELECT l.account_id,
                               DATE_FORMAT(l.created_at, '%%Y-%%m') AS stat_month,
                               COUNT(*) AS entry_count,
                               SUM(l.delta) AS sum_delta,
                               MAX(l.account_version) AS ending_version,
                               MAX(l.id) AS max_id
                        FROM pet_wallet_ledger l
                        WHERE l.created_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL %d DAY)
                        GROUP BY l.account_id, DATE_FORMAT(l.created_at, '%%Y-%%m')
                    ) g
                    ON DUPLICATE KEY UPDATE
                        entry_count = VALUES(entry_count),
                        sum_delta = VALUES(sum_delta),
                        ending_version = VALUES(ending_version),
                        ending_balance_after = VALUES(ending_balance_after)
                    """.formatted(LEDGER_ARCHIVE_AFTER_DAYS));
            if (written > 0) {
                log.info("钱包账本归档汇总完成: {} 个月度分区", written);
            }
        } catch (Exception e) {
            // 归档为附加能力，失败不影响业务与对账
            log.warn("钱包账本归档汇总失败（不阻断）", e);
        }
    }
}
