package com.cloudmart.file.scheduler;

import com.cloudmart.file.service.FileDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T27：文件删除失败恢复扫描。
 *
 * <p>删除卡在 DELETING 或孤儿实物未回收时，定时重试物理删除（幂等）并收口台账。
 * 失败继续指数退避（见 markDeleteFailed / markReclaimFailed），不会固定高频扫表。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeletionRecoveryScheduler {

    /** 单轮最多处理的批次（避免一次扫描长事务/长循环阻塞调度） */
    private static final int BATCH_SIZE = 100;

    private final FileDeletionService deletionService;

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void recover() {
        try {
            int deleting = deletionService.recoverDeleting(BATCH_SIZE);
            int orphans = deletionService.recoverOrphans(BATCH_SIZE);
            if (deleting > 0 || orphans > 0) {
                log.info("文件删除恢复扫描完成: 收口资产 {} 条, 回收孤儿 {} 个", deleting, orphans);
            }
        } catch (Exception e) {
            // 恢复扫描为兜底能力，失败不阻断其他调度
            log.warn("文件删除恢复扫描异常（不阻断）", e);
        }
    }
}
