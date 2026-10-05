package com.cloudmart.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.security.ServiceTokenSigner;
import com.cloudmart.user.entity.AccountDeletionStep;
import com.cloudmart.user.entity.AccountDeletionTask;
import com.cloudmart.user.feign.AuthStateFeignClient;
import com.cloudmart.user.feign.ErasureFeignClient;
import com.cloudmart.user.feign.OrderErasureFeignClient;
import com.cloudmart.user.repository.AccountDeletionStepMapper;
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
 * 全账号注销编排服务（T06 重构：跨域可恢复流程）。
 *
 * <p>状态机：PENDING（30 天等待期，可撤销）→ PRECHECK → EXECUTING → COMPLETED；
 * 失败为 BLOCKED（步骤台账退避重试，可恢复），<b>完成语义 = 所有必需域步骤
 * SUCCESS</b>——旧实现的 EXECUTED 掩盖局部完成（只擦了心愿域就置完成）不再存在。</p>
 *
 * <ul>
 *   <li>预检 fail-closed：远程异常、success=false、空数据一律视为"无法确认无阻断"，
 *       绝不放行（旧实现只在 success&amp;&amp;data==true 时阻止，业务失败响应被放行）；</li>
 *   <li>冻结先行：执行阶段第一步撤销全部会话（auth invalidate-state），会话死后
 *       重新复核未结订单——解决"检查后又产生业务"的竞态；</li>
 *   <li>步骤台账（user_account_deletion_step）：每域每步骤独立事实，租约防多实例
 *       重入，退避重试，重启从未完成步骤继续；</li>
 *   <li>分域执行：AUTH 会话撤销 → ORDER 未结复核 → WISH 擦除 → ORDER 收货人
 *       去标识化 → USER 主体去标识化（最后执行，登录阻断）；COMMUNITY/NOTIFICATION/
 *       FILE 三域擦除经内部端点接线（T06 补齐，幂等）；失败如实 FAILED 并退避重试——
 *       不假装已擦除，后台/进度中心可见；</li>
 *   <li>旧 EXECUTED 任务（B20 语义）不自动视作完成：扫描纳入重新核查，
 *       各步骤幂等重放，全部确认后才升 COMPLETED。</li>
 * </ul>
 */
@Service
@Slf4j
public class AccountDeletionOrchestrationService {

    private static final long GRACE_DAYS = 30;
    private static final long LEASE_SECONDS = 120;
    private static final long[] RETRY_BACKOFF_SECONDS = {60, 300, 900, 3600};
    static final String BLOCK_OPEN_ORDERS = "OPEN_ORDERS";
    static final String BLOCK_DOMAIN_NOT_WIRED = "ERASURE_DOMAIN_NOT_WIRED";

    /** 执行域清单（顺序即执行序）：会话撤销最先，主体去标识化最后（登录阻断收口） */
    private static final List<StepSpec> STEP_SPECS = List.of(
            new StepSpec("AUTH", "SESSION_REVOKE"),
            new StepSpec("ORDER", "OPEN_ORDER_CHECK"),
            new StepSpec("WISH", "ERASE"),
            new StepSpec("ORDER", "ANONYMIZE"),
            new StepSpec("USER", "ANONYMIZE"),
            new StepSpec("COMMUNITY", "ERASE"),
            new StepSpec("NOTIFICATION", "ERASE"),
            new StepSpec("FILE", "ERASE")
    );

    private record StepSpec(String domain, String step) {
    }

    private final AccountDeletionTaskMapper taskMapper;
    private final AccountDeletionStepMapper stepMapper;
    private final com.cloudmart.user.repository.UserMapper userMapper;
    private final ErasureFeignClient erasureFeignClient;
    private final OrderErasureFeignClient orderErasureFeignClient;
    private final com.cloudmart.user.feign.CommunityErasureFeignClient communityErasureFeignClient;
    private final com.cloudmart.user.feign.NotificationErasureFeignClient notificationErasureFeignClient;
    private final com.cloudmart.user.feign.FileErasureFeignClient fileErasureFeignClient;
    private final AuthStateFeignClient authStateFeignClient;
    private final com.cloudmart.user.feign.OrderBlockFeignClient orderQueryFeignClient;
    private final String instanceId;
    private final String secret;
    private volatile ServiceTokenSigner cachedSigner;

    public AccountDeletionOrchestrationService(
            AccountDeletionTaskMapper taskMapper,
            AccountDeletionStepMapper stepMapper,
            com.cloudmart.user.repository.UserMapper userMapper,
            ErasureFeignClient erasureFeignClient,
            OrderErasureFeignClient orderErasureFeignClient,
            com.cloudmart.user.feign.CommunityErasureFeignClient communityErasureFeignClient,
            com.cloudmart.user.feign.NotificationErasureFeignClient notificationErasureFeignClient,
            com.cloudmart.user.feign.FileErasureFeignClient fileErasureFeignClient,
            AuthStateFeignClient authStateFeignClient,
            com.cloudmart.user.feign.OrderBlockFeignClient orderQueryFeignClient,
            @Value("${wish.service-token.secret:${WISH_SERVICE_TOKEN_SECRET:}}") String secret,
            @Value("${account-deletion.instance-id:${HOSTNAME:deletion-default}}") String instanceId) {
        this.taskMapper = taskMapper;
        this.stepMapper = stepMapper;
        this.userMapper = userMapper;
        this.erasureFeignClient = erasureFeignClient;
        this.orderErasureFeignClient = orderErasureFeignClient;
        this.communityErasureFeignClient = communityErasureFeignClient;
        this.notificationErasureFeignClient = notificationErasureFeignClient;
        this.fileErasureFeignClient = fileErasureFeignClient;
        this.authStateFeignClient = authStateFeignClient;
        this.orderQueryFeignClient = orderQueryFeignClient;
        this.secret = secret;
        this.instanceId = instanceId;
    }

    private ServiceTokenSigner signer() {
        if (cachedSigner == null) {
            cachedSigner = new ServiceTokenSigner(secret, "mall-user", "wish:erasure",
                    Duration.ofSeconds(60), java.time.Clock.systemUTC());
        }
        return cachedSigner;
    }

    /** 申请注销（30 天宽限期；同用户仅一个任务），并初始化分域步骤台账。 */
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
                    .set(AccountDeletionTask::getBlockReason, null)
                    .set(AccountDeletionTask::getServiceProgress, null)
                    .setSql("version = version + 1"));
            if (revived == 0) {
                throw new BusinessException("WISH_STATUS_CONFLICT", "注销申请状态已变更，请刷新");
            }
            resetSteps(existing.getId(), userId);
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
        resetSteps(task.getId(), userId);
        log.warn("用户申请全账号注销 userId={}，宽限期至 {}", userId, task.getExecuteAfter());
        return task;
    }

    /** （重）建步骤台账：新任务/重新申请时全部重置为 PENDING（幂等擦除可安全重放） */
    private void resetSteps(Long taskId, Long userId) {
        stepMapper.delete(new LambdaQueryWrapper<AccountDeletionStep>()
                .eq(AccountDeletionStep::getTaskId, taskId));
        for (StepSpec spec : STEP_SPECS) {
            AccountDeletionStep step = new AccountDeletionStep();
            step.setTaskId(taskId);
            step.setUserId(userId);
            step.setDomain(spec.domain());
            step.setStep(spec.step());
            step.setStatus(AccountDeletionStep.STATUS_PENDING);
            step.setAttempts(0);
            stepMapper.insert(step);
        }
    }

    /** 取消注销（仅等待期 PENDING；进入预检/执行后不可撤销）。 */
    public AccountDeletionTask cancel(Long userId) {
        AccountDeletionTask task = getByUser(userId);
        if (task == null || "CANCELED".equals(task.getStatus())) {
            throw new BusinessException("WISH_DELETION_NOT_FOUND", "没有待执行的注销申请");
        }
        if ("COMPLETED".equals(task.getStatus()) || "EXECUTED".equals(task.getStatus())) {
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

    /**
     * T06 运营重试：失败步骤立即清零退避可重试（PENDING/FAILED → 可认领），
     * 任务置 BLOCKED 等待扫描接管；不提供跳过资金检查能力——
     * OPEN_ORDER_CHECK 未通过时重试只会再次被拦下。
     */
    public void retryFailedSteps(Long taskId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "重试必须填写运营原因（审计）");
        }
        AccountDeletionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("WISH_DELETION_NOT_FOUND", "注销任务不存在");
        }
        if ("CANCELED".equals(task.getStatus())) {
            throw new BusinessException("AFTER_SALE_STATUS_ERROR", "任务已撤销，不能重试");
        }
        int reset = stepMapper.update(null, new LambdaUpdateWrapper<AccountDeletionStep>()
                .eq(AccountDeletionStep::getTaskId, taskId)
                .eq(AccountDeletionStep::getStatus, AccountDeletionStep.STATUS_FAILED)
                .set(AccountDeletionStep::getStatus, AccountDeletionStep.STATUS_PENDING)
                .set(AccountDeletionStep::getNextRetryAt, LocalDateTime.now(ZoneId.of("UTC"))));
        taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                .eq(AccountDeletionTask::getId, taskId)
                .set(AccountDeletionTask::getStatus, "BLOCKED")
                .set(AccountDeletionTask::getBlockReason, "OPERATOR_RETRY:" + reason)
                .set(AccountDeletionTask::getUpdatedAt, LocalDateTime.now(ZoneId.of("UTC"))));
        log.warn("[T06] 运营重试注销失败步骤 taskId={} reset={} reason={}", taskId, reset, reason);
    }

    /** 用户本人进度（含分域步骤，脱敏：不含任何 PII/原始错误堆栈） */
    public List<AccountDeletionStep> stepsOf(Long taskId) {
        return stepMapper.selectList(new LambdaQueryWrapper<AccountDeletionStep>()
                .eq(AccountDeletionStep::getTaskId, taskId)
                .orderByAsc(AccountDeletionStep::getId));
    }

    /**
     * 到期/可恢复扫描（每 5 分钟）：等待期到期 → PRECHECK；BLOCKED（有可重试步骤
     * 到期）→ 继续执行；EXECUTING 租约过期（进程崩溃遗留）→ 接管续跑；
     * 旧 EXECUTED（B20 语义）→ 重新核查全部步骤后才升 COMPLETED。
     */
    @Scheduled(fixedDelay = 300_000)
    public void executeDueScan() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime staleBefore = now.minusMinutes(30);
        List<AccountDeletionTask> due = taskMapper.selectList(
                new LambdaQueryWrapper<AccountDeletionTask>()
                        .and(w -> w
                                // 等待期到期
                                .eq(AccountDeletionTask::getStatus, "PENDING")
                                .le(AccountDeletionTask::getExecuteAfter, now)
                                // 执行中租约过期（崩溃接管）
                                .or(x -> x.eq(AccountDeletionTask::getStatus, "EXECUTING")
                                        .lt(AccountDeletionTask::getUpdatedAt, staleBefore))
                                // 阻断后有可重试步骤到期
                                .or(x -> x.eq(AccountDeletionTask::getStatus, "BLOCKED")
                                        .lt(AccountDeletionTask::getUpdatedAt, now))
                                // 旧语义补核查
                                .or(x -> x.eq(AccountDeletionTask::getStatus, "EXECUTED")))
                        .last("LIMIT 50"));
        for (AccountDeletionTask task : due) {
            executeTask(task);
        }
    }

    void executeTask(AccountDeletionTask task) {
        LocalDateTime staleBefore = LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(30);
        String originalStatus = task.getStatus();
        // 旧 EXECUTED（B20 语义）保持原状态进入核查轮：全部步骤幂等重放确认后才升 COMPLETED
        String target = "EXECUTED".equals(originalStatus) ? "EXECUTED" : "EXECUTING";
        int claimed = taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                .eq(AccountDeletionTask::getId, task.getId())
                .and(w -> w.eq(AccountDeletionTask::getStatus, "PENDING")
                        .or(x -> x.eq(AccountDeletionTask::getStatus, "BLOCKED"))
                        .or(y -> y.eq(AccountDeletionTask::getStatus, "EXECUTING")
                                .lt(AccountDeletionTask::getUpdatedAt, staleBefore))
                        .or(z -> z.eq(AccountDeletionTask::getStatus, "EXECUTED")))
                .set(AccountDeletionTask::getStatus, target)
                .set(AccountDeletionTask::getUpdatedAt, LocalDateTime.now(ZoneId.of("UTC"))));
        if (claimed == 0) {
            return;
        }
        try {
            if ("PENDING".equals(originalStatus)) {
                // 等待期届满首次进入：预检（fail-closed），通过才冻结+执行
                if (!precheckOpenOrders(task)) {
                    return;
                }
            }
            freezeSessions(task);
            executeSteps(task);
        } catch (Exception ex) {
            taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                    .eq(AccountDeletionTask::getId, task.getId())
                    .set(AccountDeletionTask::getStatus, "BLOCKED")
                    .set(AccountDeletionTask::getBlockReason, "EXECUTION_ERROR")
                    .set(AccountDeletionTask::getUpdatedAt, LocalDateTime.now(ZoneId.of("UTC"))));
            log.error("注销执行异常（停留 BLOCKED 可恢复）userId={}", task.getUserId(), ex);
        }
    }

    /**
     * T06 预检（fail-closed）：仅当权威应答明确 success 且 data==false（无未结订单）
     * 才放行；远程异常/success=false/空数据均视为"无法确认无阻断"→ BLOCKED。
     * 账号置 DELETING 冻结写入前，绝不允许带着未结事项进入执行。
     */
    private boolean precheckOpenOrders(AccountDeletionTask task) {
        try {
            var blocking = orderQueryFeignClient.hasOpenOrders(task.getUserId(),
                    signer().sign("mall-order"));
            if (blocking != null && blocking.success() && Boolean.FALSE.equals(blocking.data())) {
                return true;
            }
            String reason = blocking == null || !blocking.success()
                    ? "ORDER_CHECK_UNAVAILABLE"
                    : BLOCK_OPEN_ORDERS;
            blockTask(task, reason);
            log.warn("注销预检未通过（{}），停留 BLOCKED userId={}", reason, task.getUserId());
            return false;
        } catch (Exception ex) {
            blockTask(task, "ORDER_CHECK_UNAVAILABLE");
            log.warn("注销预检调用失败（fail-closed），停留 BLOCKED userId={}: {}",
                    task.getUserId(), ex.getMessage());
            return false;
        }
    }

    /** 冻结先行：撤销全部会话（authVersion 递增 + 刷新令牌家族撤销）——冻结新业务写入 */
    private void freezeSessions(AccountDeletionTask task) {
        StepSpec spec = new StepSpec("AUTH", "SESSION_REVOKE");
        AccountDeletionStep step = claimStep(task.getId(), spec);
        if (step == null) {
            return;
        }
        try {
            var resp = authStateFeignClient.invalidateState(AuthStateFeignClient.hardInvalidate(
                    new AuthStateFeignClient.SubjectBody("USER", task.getUserId())));
            if (resp == null || !resp.success()) {
                failStep(task, step, "AUTH_INVALIDATE_FAILED");
                return;
            }
            stepMapper.markSuccess(step.getId(), instanceId);
            log.info("[T06] 会话已全部撤销（冻结生效）userId={}", task.getUserId());
        } catch (Exception ex) {
            failStep(task, step, "AUTH_REVOKE_ERROR");
            log.warn("[T06] 会话撤销调用失败 userId={}: {}", task.getUserId(), ex.getMessage());
        }
    }

    /** 分域步骤执行：租约认领逐条推进，全部 SUCCESS 才 COMPLETED */
    private void executeSteps(AccountDeletionTask task) {
        List<AccountDeletionStep> steps = stepsOf(task.getId());
        if (steps.isEmpty()) {
            // T06 真实环境暴露：V5 迁移前的存量任务无步骤行——空台账会空跑成
            // COMPLETED 跳过全部擦除。重建步骤台账（幂等），本轮先按未完成处理
            log.warn("[T06] 步骤台账为空（存量任务），已重建 userId={} taskId={}",
                    task.getUserId(), task.getId());
            resetSteps(task.getId(), task.getUserId());
        }
        boolean allSuccess = true;
        String blockReason = null;
        for (AccountDeletionStep step : steps) {
            if (AccountDeletionStep.STATUS_SUCCESS.equals(step.getStatus())) {
                continue;
            }
            if (AccountDeletionStep.STATUS_RUNNING.equals(step.getStatus())
                    && step.getLeaseUntil() != null
                    && step.getLeaseUntil().isAfter(LocalDateTime.now(ZoneId.of("UTC")))) {
                // 其他实例租约执行中：本轮等待
                allSuccess = false;
                continue;
            }
            AccountDeletionStep claimed = claimStep(task.getId(),
                    new StepSpec(step.getDomain(), step.getStep()));
            if (claimed == null) {
                allSuccess = false;
                continue;
            }
            String error = runStep(task, claimed);
            if (error == null) {
                stepMapper.markSuccess(claimed.getId(), instanceId);
                log.info("[T06] 步骤完成 {}.{} userId={}", claimed.getDomain(), claimed.getStep(),
                        task.getUserId());
            } else {
                failStep(task, claimed, error);
                allSuccess = false;
                blockReason = error;
            }
        }
        if (allSuccess) {
            taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                    .eq(AccountDeletionTask::getId, task.getId())
                    .set(AccountDeletionTask::getStatus, "COMPLETED")
                    .set(AccountDeletionTask::getBlockReason, null)
                    .set(AccountDeletionTask::getExecutedAt, LocalDateTime.now(ZoneId.of("UTC")))
                    .set(AccountDeletionTask::getUpdatedAt, LocalDateTime.now(ZoneId.of("UTC"))));
            log.warn("[T06] 全账号注销全域完成（全部步骤 SUCCESS）userId={}", task.getUserId());
        } else {
            taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                    .eq(AccountDeletionTask::getId, task.getId())
                    .set(AccountDeletionTask::getStatus, "BLOCKED")
                    .set(AccountDeletionTask::getBlockReason,
                            blockReason == null ? "STEP_IN_PROGRESS" : blockReason)
                    .set(AccountDeletionTask::getUpdatedAt, LocalDateTime.now(ZoneId.of("UTC"))));
            log.warn("[T06] 注销步骤未全部完成，停留 BLOCKED（退避重试）userId={} block={}",
                    task.getUserId(), blockReason);
        }
    }

    /**
     * 单步执行。@return null=成功；否则错误码（脱敏，写入台账 last_error）
     */
    private String runStep(AccountDeletionTask task, AccountDeletionStep step) {
        String domain = step.getDomain();
        String stepName = step.getStep();
        try {
            switch (domain + "/" + stepName) {
                case "ORDER/OPEN_ORDER_CHECK" -> {
                    // 冻结后复核：会话已死，不会再产生新订单——此刻仍有未结订单即真阻断
                    var blocking = orderQueryFeignClient.hasOpenOrders(task.getUserId(),
                            signer().sign("mall-order"));
                    if (blocking == null || !blocking.success()) {
                        return "ORDER_CHECK_UNAVAILABLE";
                    }
                    return Boolean.TRUE.equals(blocking.data()) ? BLOCK_OPEN_ORDERS : null;
                }
                case "WISH/ERASE" -> {
                    var resp = erasureFeignClient.eraseWishData(task.getUserId(),
                            signer().sign("mall-wish"));
                    return resp != null && resp.success() && Boolean.TRUE.equals(resp.data())
                            ? null : "WISH_ERASE_FAILED";
                }
                case "ORDER/ANONYMIZE" -> {
                    var resp = orderErasureFeignClient.anonymizeReceiver(task.getUserId());
                    return resp != null && resp.success() ? null : "ORDER_ANONYMIZE_FAILED";
                }
                case "USER/ANONYMIZE" -> {
                    anonymizeLocalAccount(task.getUserId());
                    return null;
                }
                case "COMMUNITY/ERASE" -> {
                    var resp = communityErasureFeignClient.erase(task.getUserId());
                    return resp != null && resp.success() ? null : "COMMUNITY_ERASE_FAILED";
                }
                case "NOTIFICATION/ERASE" -> {
                    var resp = notificationErasureFeignClient.erase(task.getUserId());
                    return resp != null && resp.success() ? null : "NOTIFICATION_ERASE_FAILED";
                }
                case "FILE/ERASE" -> {
                    var resp = fileErasureFeignClient.erase(task.getUserId());
                    return resp != null && resp.success() ? null : "FILE_ERASE_FAILED";
                }
                default -> {
                    log.error("[T06] 未知注销步骤 {}.{}", domain, stepName);
                    return "UNKNOWN_STEP";
                }
            }
        } catch (Exception ex) {
            log.warn("[T06] 步骤执行异常 {}.{} userId={}: {}", domain, stepName,
                    task.getUserId(), ex.getMessage());
            return "STEP_EXECUTION_ERROR";
        }
    }

    /** 主体去标识化（最后执行）：登录阻断 + PII 就地脱敏；username 保留供审计对账 */
    private void anonymizeLocalAccount(Long userId) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.user.entity.User>()
                .eq(com.cloudmart.user.entity.User::getId, userId)
                .ne(com.cloudmart.user.entity.User::getStatus, 0)
                .set(com.cloudmart.user.entity.User::getStatus, 0)
                .set(com.cloudmart.user.entity.User::getEmail, "deleted+" + userId + "@invalid.local")
                .set(com.cloudmart.user.entity.User::getNickname, "已注销用户")
                .set(com.cloudmart.user.entity.User::getAvatar, "")
                .set(com.cloudmart.user.entity.User::getSignature, "")
                .set(com.cloudmart.user.entity.User::getBirthday, "")
                .set(com.cloudmart.user.entity.User::getSchool, "")
                .set(com.cloudmart.user.entity.User::getLocation, "")
                .set(com.cloudmart.user.entity.User::getOccupation, "")
                .set(com.cloudmart.user.entity.User::getDeletedAt, LocalDateTime.now(ZoneId.of("UTC"))));
        if (updated == 0) {
            log.info("[T06] 用户主体已去标识化（幂等跳过）userId={}", userId);
        }
    }

    /** 台账认领：到期步骤 → RUNNING + 租约（多实例互斥） */
    private AccountDeletionStep claimStep(Long taskId, StepSpec spec) {
        AccountDeletionStep step = stepMapper.selectOne(new LambdaQueryWrapper<AccountDeletionStep>()
                .eq(AccountDeletionStep::getTaskId, taskId)
                .eq(AccountDeletionStep::getDomain, spec.domain())
                .eq(AccountDeletionStep::getStep, spec.step()));
        if (step == null) {
            return null;
        }
        int claimed = stepMapper.claim(step.getId(), instanceId,
                LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(LEASE_SECONDS));
        return claimed == 1 ? step : null;
    }

    /** 步骤失败回写：退避重试（次数越多间隔越长），保持可恢复 */
    private void failStep(AccountDeletionTask task, AccountDeletionStep step, String error) {
        int attempts = step.getAttempts() == null || step.getAttempts() < 1
                ? 1 : step.getAttempts();
        long backoff = RETRY_BACKOFF_SECONDS[Math.min(
                attempts - 1, RETRY_BACKOFF_SECONDS.length - 1)];
        stepMapper.markFailed(step.getId(), instanceId, AccountDeletionStep.STATUS_FAILED,
                LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(backoff), error);
    }

    private void blockTask(AccountDeletionTask task, String reason) {
        taskMapper.update(null, new LambdaUpdateWrapper<AccountDeletionTask>()
                .eq(AccountDeletionTask::getId, task.getId())
                .set(AccountDeletionTask::getStatus, "BLOCKED")
                .set(AccountDeletionTask::getBlockReason, reason)
                .set(AccountDeletionTask::getUpdatedAt, LocalDateTime.now(ZoneId.of("UTC"))));
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
