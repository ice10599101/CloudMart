package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetPurchaseOrder;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * P02 购买幂等恢复扫描器（QA35）：清理执行者崩溃残留的 PROCESSING 请求键。
 *
 * <p>恢复顺序（方案 P02："查本地业务事实后完成/重试，不先重扣"）：</p>
 * <ol>
 *   <li>扫描租约到期的 PROCESSING 行（endpoint=PURCHASE，有界批量）；</li>
 *   <li>CAS 接管租约（竞态败者留给下一轮/同键重试）；</li>
 *   <li>按 (user_id, request_key) 查本地购买订单事实：
 *       订单 COMPLETED → 按事实重建终态响应完成幂等键（不重扣、不重发）；
 *       无已提交业务事实 → 置 FAILED 释放同键重试（业务事务原子回滚保证此时没有任何扣款）。</li>
 * </ol>
 *
 * <p>业务事务与 dedup 终态原子提交后，"订单 COMPLETED 而 dedup PROCESSING"只可能来自
 * 执行者卡死/进程崩溃——本扫描器是唯一权威收敛入口，恢复结果与原请求键绑定，
 * 客户端用原键查询即可拿回结果。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetPurchaseRecoveryService {

    static final String ENDPOINT_KEY = PetPurchaseApplicationService.ENDPOINT_KEY;
    private static final int BATCH_LIMIT = 100;

    private final PetRequestDedupService dedupService;
    private final com.cloudmart.pet.config.PetMetrics metrics;
    private final PetRequestDedupMapper dedupMapper;
    private final PetPurchaseOrderMapper orderMapper;
    private final PetPurchaseApplicationService purchaseApplicationService;

    @Scheduled(fixedDelayString = "${pet.purchase.recovery-interval-ms:60000}",
            initialDelayString = "${pet.purchase.recovery-initial-delay-ms:30000}")
    public void scheduledRecovery() {
        int recovered = recoverExpiredLeases();
        if (recovered > 0) {
            log.info("购买幂等恢复扫描完成, recovered={}", recovered);
        }
    }

    /** 恢复一批租约到期的 PROCESSING 购买请求；@return 实际接管并收敛的条数 */
    public int recoverExpiredLeases() {
        List<PetRequestDedup> expired = dedupMapper.selectList(new LambdaQueryWrapper<PetRequestDedup>()
                .eq(PetRequestDedup::getEndpointKey, ENDPOINT_KEY)
                .eq(PetRequestDedup::getStatus, "PROCESSING")
                .lt(PetRequestDedup::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC))
                .last("LIMIT " + BATCH_LIMIT));
        int recovered = 0;
        for (PetRequestDedup row : expired) {
            if (!dedupService.tryTakeover(row.getUserId(), row.getEndpointKey(), row.getRequestKey())) {
                continue;
            }
            try {
                recoverOne(row);
                recovered++;
            } catch (Exception e) {
                // 单行失败不阻断批次；行已被本执行者持租，下一轮租约到期后再次接管
                log.error("购买幂等恢复单行失败, userId={}, requestKey={}",
                        row.getUserId(), row.getRequestKey(), e);
            }
        }
        return recovered;
    }

    private void recoverOne(PetRequestDedup row) {
        PetPurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PetPurchaseOrder>()
                .eq(PetPurchaseOrder::getUserId, row.getUserId())
                .eq(PetPurchaseOrder::getRequestKey, row.getRequestKey()));
        if (order != null && "COMPLETED".equals(order.getStatus())) {
            // 业务事实已成立：按订单/流水/资产事实重建终态，不产生任何新业务事实
            PetPurchaseApplicationService.PurchaseResult rebuilt =
                    purchaseApplicationService.rebuildResult(row.getUserId(), row.getRequestKey(), order);
            // R02 fencing：接管成功后重读行取当前租约归属，终态回写匹配 owner（旧执行者失效）
            PetRequestDedup current = dedupMapper.selectOne(new LambdaQueryWrapper<PetRequestDedup>()
                    .eq(PetRequestDedup::getUserId, row.getUserId())
                    .eq(PetRequestDedup::getEndpointKey, row.getEndpointKey())
                    .eq(PetRequestDedup::getRequestKey, row.getRequestKey()));
            dedupService.completeSucceeded(row.getUserId(), row.getEndpointKey(), row.getRequestKey(),
                    current == null ? null : current.getLeaseOwner(),
                    order.getId(), PetJsonUtils.toJson(rebuilt));
            log.info("购买幂等按业务事实恢复完成, userId={}, requestKey={}, orderId={}",
                    row.getUserId(), row.getRequestKey(), order.getId());
            return;
        }
        // 无已提交业务事实（业务事务原子回滚或从未开始）：置 FAILED 释放同键重试，重试不会重复扣款
        PetRequestDedup current = dedupMapper.selectOne(new LambdaQueryWrapper<PetRequestDedup>()
                .eq(PetRequestDedup::getUserId, row.getUserId())
                .eq(PetRequestDedup::getEndpointKey, row.getEndpointKey())
                .eq(PetRequestDedup::getRequestKey, row.getRequestKey()));
        dedupService.markFailed(row.getUserId(), row.getEndpointKey(), row.getRequestKey(),
                current == null ? null : current.getLeaseOwner(),
                PetJsonUtils.toJson(Map.of("error", "LEASE_EXPIRED_NO_COMMITTED_FACT")));
        log.info("购买幂等残留无业务事实，置为可重试, userId={}, requestKey={}",
                row.getUserId(), row.getRequestKey());
    }
}
