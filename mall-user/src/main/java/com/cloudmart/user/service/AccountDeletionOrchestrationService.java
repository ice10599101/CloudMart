package com.cloudmart.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.security.ServiceTokenSigner;
import com.cloudmart.user.entity.AccountDeletionTask;
import com.cloudmart.user.feign.ErasureFeignClient;
import com.cloudmart.user.repository.AccountDeletionTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 全账号注销编排服务（B20）：统一由 mall-user 编排跨服务数据擦除。
 *
 * <p>状态机：PENDING → EXECUTING → EXECUTED/FAILED（可 CANCELED）。</p>
 * <ul>
 *   <li>取消：CAS PENDING 且未过截止；与到期执行并发时只有一个成功；</li>
 *   <li>执行：CAS PENDING→EXECUTING 认领（多实例单执行者），逐服务幂等擦除
 *       （wish 内部端点，服务令牌 iss=mall-user scope=wish:erasure），全部成功才
 *       EXECUTED；任一失败记 service_progress 并回退 PENDING 下轮重试；</li>
 *   <li>密钥惰性签发（B20 开发体验）：本地未注入 WISH_SERVICE_TOKEN_SECRET 时
 *       服务可启动，到期执行时才报明确错误并保持任务待重试。</li>
 * </ul>
 */
@Service
@Slf4j
public class AccountDeletionOrchestrationService {

    private static final long GRACE_DAYS = 30;

    private final AccountDeletionTaskMapper taskMapper;
    private final ErasureFeignClient erasureFeignClient;
    private final com.cloudmart.user.feign.OrderBlockFeignClient orderQueryFeignClient;
    private final String secret;
    private volatile ServiceTokenSigner cachedSigner;

    public AccountDeletionOrchestrationService(
            AccountDeletionTaskMapper taskMapper,
            ErasureFeignClient erasureFeignClient,
            com.cloudmart.user.feign.OrderBlockFeignClient orderQueryFeignClient,
            @Value("${wish.service-token.secret:${WISH_SERVICE_TOKEN_SECRET:}}") String secret) {
        this.taskMapper = taskMapper;
        this.erasureFeignClient = erasureFeignClient;
        this.orderQueryFeignClient = orderQueryFeignClient;
        this.secret = secret;
    }

    private ServiceTokenSigner signer() {
        if (cachedSigner == null) {
            cachedSigner = new ServiceTokenSigner(secret, "mall-user", "wish:erasure",
                    Duration.ofSeconds(60), java.time.Clock.systemUTC());
        }
        return cachedSigner;
    }

    /** 申请注销（30 天宽限期；同用户仅一个任务）。
     *
     *  <p>USER-01：CANCELED 任务 CAS 复活为新一轮 PENDING（申请→取消→再申请闭环）——
     *  修复 uk_deletion_task_user 唯一键下再次 insert 必然冲突的断点；每次申请的
     *  历史通过 updated_at/version 体现，原行不删除（保留取消痕迹审计）。</p>
     */
    public AccountDeletionTask apply(Long userId, String reason) {
        AccountDeletionTask existing = getByUser(userId);
        if (existing != null && !"CANCELED".equals(existing.getStatus())) {
            throw new BusinessException("WISH_DELETION_PENDING", "已存在注销申请");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (existing != null) {
            // CAS 复活：仅 CANCELED 行可被重新激活（并发申请只有一个赢家）
            int revived = taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                    .eq(AccountDeletionTask::getId, existing.getId())
                    .eq(AccountDeletionTask::getStatus, "CANCELED")
                    .set(AccountDeletionTask::getStatus, "PENDING")
                    .set(AccountDeletionTask::getReason, reason)
                    .set(AccountDeletionTask::getRequestedAt, now)
                    .set(AccountDeletionTask::getExecuteAfter, now.plusDays(GRACE_DAYS))
                    .set(AccountDeletionTask::getCanceledAt, null)
                    .set(AccountDeletionTask::getExecutedAt, null)
                    .set(AccountDeletionTask::getServiceProgress, null)
                    .setSql("version = version + 1"));
            if (revived == 0) {
                throw new BusinessException("WISH_STATUS_CONFLICT", "注销申请状态已变更，请刷新");
            }
            log.warn("用户重新申请全账号注销 userId={}，宽限期至 {}", userId, existing.getExecuteAfter());
            return getByUser(userId);
        }
        AccountDeletionTask task = new AccountDeletionTask();
        task.setUserId(userId);
        task.setStatus("PENDING");
        task.setReason(reason);
        task.setRequestedAt(now);
        task.setExecuteAfter(task.getRequestedAt().plusDays(GRACE_DAYS));
        task.setVersion(0);
        taskMapper.insert(task);
        log.warn("用户申请全账号注销 userId={}，宽限期至 {}", userId, task.getExecuteAfter());
        return task;
    }

    /** 取消注销（CAS PENDING 且未过截止）。 */
    public AccountDeletionTask cancel(Long userId) {
        AccountDeletionTask task = getByUser(userId);
        if (task == null || "CANCELED".equals(task.getStatus())) {
            throw new BusinessException("WISH_DELETION_NOT_FOUND", "没有待执行的注销申请");
        }
        if ("EXECUTED".equals(task.getStatus())) {
            throw new BusinessException("WISH_DELETION_EXECUTED", "已执行注销，不可撤回");
        }
        int affected = taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                .eq(AccountDeletionTask::getId, task.getId())
                .eq(AccountDeletionTask::getStatus, "PENDING")
                .gt(AccountDeletionTask::getExecuteAfter, LocalDateTime.now(ZoneId.of("UTC")))
                .set(AccountDeletionTask::getStatus, "CANCELED")
                .set(AccountDeletionTask::getCanceledAt, LocalDateTime.now(ZoneId.of("UTC"))));
        if (affected == 0) {
            throw new BusinessException("WISH_STATUS_CONFLICT", "注销申请状态已变更，请刷新");
        }
        return getByUser(userId);
    }

    public AccountDeletionTask getByUser(Long userId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AccountDeletionTask>()
                .eq(AccountDeletionTask::getUserId, userId)
                .orderByDesc(AccountDeletionTask::getId)
                .last("LIMIT 1"));
    }

    /** 到期执行扫描（每 10 分钟；多实例经 CAS 认领单执行者）。 */
    @Scheduled(fixedDelay = 600_000)
    public void executeDueScan() {
        List<AccountDeletionTask> due = taskMapper.selectList(
                new LambdaQueryWrapper<AccountDeletionTask>()
                        .eq(AccountDeletionTask::getStatus, "PENDING")
                        .le(AccountDeletionTask::getExecuteAfter, LocalDateTime.now(ZoneId.of("UTC")))
                        .last("LIMIT 50"));
        for (AccountDeletionTask task : due) {
            executeTask(task);
        }
    }

    void executeTask(AccountDeletionTask task) {
        // USER-01：CAS 认领扩展——EXECUTING 超过 30 分钟（进程崩溃遗留）允许其他
        // 实例接管（stale 租约）；活跃 EXECUTING 不可抢
        LocalDateTime staleBefore = LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(30);
        int claimed = taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                .eq(AccountDeletionTask::getId, task.getId())
                .and(w -> w.eq(AccountDeletionTask::getStatus, "PENDING")
                        .or(x -> x.eq(AccountDeletionTask::getStatus, "EXECUTING")
                                .lt(AccountDeletionTask::getUpdatedAt, staleBefore)))
                .set(AccountDeletionTask::getStatus, "EXECUTING")
                .set(AccountDeletionTask::getUpdatedAt, LocalDateTime.now(ZoneId.of("UTC"))));
        if (claimed == 0) {
            return;
        }
        try {
            // USER-01：未结订单阻塞——有 PENDING_PAYMENT/PAID/SHIPPED 未结订单时
            // 不执行注销，返回明确阻塞原因（宽限期内用户应自行处理）
            var blocking = orderQueryFeignClient.hasOpenOrders(task.getUserId(),
                    signer().sign("mall-order"));
            if (blocking.success() && Boolean.TRUE.equals(blocking.data())) {
                taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                        .eq(AccountDeletionTask::getId, task.getId())
                        .eq(AccountDeletionTask::getStatus, "EXECUTING")
                        .set(AccountDeletionTask::getStatus, "PENDING")
                        .set(AccountDeletionTask::getServiceProgress,
                            "{\"blocked\":\"OPEN_ORDERS\"}"));
                log.warn("注销被未结订单阻塞，回退待重试 userId={}", task.getUserId());
                return;
            }

            Map<String, Object> progress = new java.util.LinkedHashMap<>();
            boolean allSuccess = true;

            // wish 数据擦除（幂等：重复调用返回原结果）
            try {
                com.cloudmart.common.api.ApiResponse<Boolean> resp =
                        erasureFeignClient.eraseWishData(task.getUserId(), signer().sign("mall-wish"));
                boolean ok = resp.success() && Boolean.TRUE.equals(resp.data());
                progress.put("wish", ok ? "SUCCESS" : "FAILED");
                allSuccess &= ok;
            } catch (Exception ex) {
                progress.put("wish", "FAILED");
                allSuccess = false;
                log.warn("wish 数据擦除调用失败 userId={}: {}", task.getUserId(), ex.getMessage());
            }

            if (allSuccess) {
                taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                        .eq(AccountDeletionTask::getId, task.getId())
                        .eq(AccountDeletionTask::getStatus, "EXECUTING")
                        .set(AccountDeletionTask::getStatus, "EXECUTED")
                        .set(AccountDeletionTask::getServiceProgress, toJson(progress))
                        .set(AccountDeletionTask::getExecutedAt, LocalDateTime.now(ZoneId.of("UTC"))));
                log.warn("全账号注销执行完成 userId={}", task.getUserId());
            } else {
                // 回退 PENDING 下轮重试（各服务擦除幂等）
                taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                        .eq(AccountDeletionTask::getId, task.getId())
                        .eq(AccountDeletionTask::getStatus, "EXECUTING")
                        .set(AccountDeletionTask::getStatus, "PENDING")
                        .set(AccountDeletionTask::getServiceProgress, toJson(progress)));
                log.warn("注销部分服务未完成，已回退待重试 userId={} progress={}", task.getUserId(), progress);
            }
        } catch (Exception ex) {
            taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                    .eq(AccountDeletionTask::getId, task.getId())
                    .eq(AccountDeletionTask::getStatus, "EXECUTING")
                    .set(AccountDeletionTask::getStatus, "PENDING"));
            log.error("注销执行异常 userId={}（已回退待重试）", task.getUserId(), ex);
        }
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
