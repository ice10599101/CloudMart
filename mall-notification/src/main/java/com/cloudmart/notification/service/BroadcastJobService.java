package com.cloudmart.notification.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.util.Ids;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.BroadcastJobRequest;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.entity.Notification;
import com.cloudmart.notification.entity.NotificationBroadcastItem;
import com.cloudmart.notification.entity.NotificationBroadcastJob;
import com.cloudmart.notification.feign.UserFeignClient;
import com.cloudmart.notification.repository.NotificationBroadcastItemMapper;
import com.cloudmart.notification.repository.NotificationBroadcastJobMapper;
import com.cloudmart.notification.repository.NotificationMapper;
import com.cloudmart.notification.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知群发批次任务（ADMIN-03）。
 *
 * <p>群发以批次落库：返回 jobId 与 目标/成功/失败/跳过 计数；提交幂等键
 * {@code request_id} 保证重复提交返回原批次不重复投递；明细按 (job,user) 唯一，
 * 失败项可安全重试（notification_id 为投递成功事实，重试据此跳过）。</p>
 *
 * <p>受众固定全站用户，禁用账号（status=0）记 SKIPPED 不投递。
 * 刻意不使用大事务：逐用户独立事务提交（通知 + 明细原子），失败只影响单人。</p>
 */
@Slf4j
@Service
public class BroadcastJobService {

    private static final int USER_PAGE_SIZE = 500;
    /** 广播最大枚举页数（500/页 × 200 页 = 10 万用户），防分页契约异常死循环 */
    private static final int MAX_PAGES = 200;
    private static final DateTimeFormatter JOB_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NotificationBroadcastJobMapper jobMapper;
    private final NotificationBroadcastItemMapper itemMapper;
    private final NotificationMapper notificationMapper;
    private final UserFeignClient userFeignClient;
    private final WebSocketSessionManager sessionManager;
    private final NotificationConverter notificationConverter;
    private final TransactionTemplate transactionTemplate;

    public BroadcastJobService(NotificationBroadcastJobMapper jobMapper,
                               NotificationBroadcastItemMapper itemMapper,
                               NotificationMapper notificationMapper,
                               UserFeignClient userFeignClient,
                               WebSocketSessionManager sessionManager,
                               NotificationConverter notificationConverter,
                               TransactionTemplate transactionTemplate) {
        this.jobMapper = jobMapper;
        this.itemMapper = itemMapper;
        this.notificationMapper = notificationMapper;
        this.userFeignClient = userFeignClient;
        this.sessionManager = sessionManager;
        this.notificationConverter = notificationConverter;
        this.transactionTemplate = transactionTemplate;
    }

    /** 后台批次视图（列表/详情/重试共用） */
    public record BroadcastJobSummary(String jobNo, String type, String title,
                                      int target, int delivered, int failed, int skipped,
                                      String status, Long createdBy, LocalDateTime createdAt) {
    }

    public record BroadcastItemView(Long userId, String status, Long notificationId,
                                    String error, LocalDateTime createdAt) {
    }

    public record BroadcastJobDetail(BroadcastJobSummary job, List<BroadcastItemView> items) {
    }

    /** 提交群发批次：requestId 幂等，重复提交返回原批次 */
    @SentinelResource("broadcast-job-start")
    public BroadcastJobSummary start(BroadcastJobRequest request, Long operatorId) {
        NotificationBroadcastJob existing = jobMapper.selectOne(new LambdaQueryWrapper<NotificationBroadcastJob>()
                .eq(NotificationBroadcastJob::getRequestId, request.requestId())
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("[ADMIN03] 群发重复提交按原批次返回 jobNo={}, requestId={}",
                    existing.getJobNo(), request.requestId());
            return toSummary(existing);
        }

        NotificationBroadcastJob job = new NotificationBroadcastJob();
        job.setJobNo(nextJobNo());
        job.setRequestId(request.requestId());
        job.setType(request.type());
        job.setTitle(request.title());
        job.setContent(request.content());
        job.setTargetCount(0);
        job.setDeliveredCount(0);
        job.setFailedCount(0);
        job.setSkippedCount(0);
        job.setStatus("RUNNING");
        job.setCreatedBy(operatorId);
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException e) {
            // 并发同 requestId 双提交：uk 兜底，返回先到批次
            NotificationBroadcastJob winner = jobMapper.selectOne(new LambdaQueryWrapper<NotificationBroadcastJob>()
                    .eq(NotificationBroadcastJob::getRequestId, request.requestId())
                    .last("LIMIT 1"));
            if (winner == null) {
                throw e;
            }
            return toSummary(winner);
        }

        List<UserRef> users = enumerateUsers();
        List<UserRef> enabled = new ArrayList<>();
        List<UserRef> disabled = new ArrayList<>();
        for (UserRef user : users) {
            // status=0 为禁用账号：记 SKIPPED（可追溯），不投递
            (user.status() != null && user.status() == 0 ? disabled : enabled).add(user);
        }
        job.setTargetCount(users.size());
        job.setSkippedCount(disabled.size());
        jobMapper.updateById(job);

        for (UserRef skippedUser : disabled) {
            insertItem(job.getId(), skippedUser.id(), "SKIPPED", null, "账号禁用");
        }

        int delivered = 0;
        int failed = 0;
        for (UserRef user : enabled) {
            try {
                Notification entity = transactionTemplate.execute(status -> {
                    Notification notification = buildNotification(user.id(), request.type(),
                            request.title(), request.content());
                    notificationMapper.insert(notification);
                    NotificationBroadcastItem item = new NotificationBroadcastItem();
                    item.setJobId(job.getId());
                    item.setUserId(user.id());
                    item.setStatus("DELIVERED");
                    item.setNotificationId(notification.getId());
                    try {
                        itemMapper.insert(item);
                    } catch (DuplicateKeyException e) {
                        // 同 (job,user) 已投递（并发/重试竞态）：幂等成功，回滚本次通知
                        status.setRollbackOnly();
                        return null;
                    }
                    return notification;
                });
                if (entity != null) {
                    delivered++;
                    pushQuietly(user.id(), entity);
                }
            } catch (Exception e) {
                failed++;
                log.warn("[ADMIN03] 群发单用户投递失败 jobNo={}, userId={}: {}",
                        job.getJobNo(), user.id(), e.getMessage());
                insertItemQuietly(job.getId(), user.id(), "FAILED", e.getMessage());
            }
        }

        job.setDeliveredCount(delivered);
        job.setFailedCount(failed);
        job.setStatus(failed == 0 ? "COMPLETED" : delivered > 0 ? "PARTIAL" : "FAILED");
        jobMapper.updateById(job);
        log.info("[ADMIN03] 群发完成 jobNo={}, target={}, delivered={}, failed={}, skipped={}",
                job.getJobNo(), job.getTargetCount(), delivered, failed, job.getSkippedCount());
        return toSummary(job);
    }

    /** 重试失败项：CAS 认领 + notification_id 幂等，重复重试不重复投递 */
    @SentinelResource("broadcast-job-retry")
    public BroadcastJobSummary retry(String jobNo) {
        NotificationBroadcastJob job = jobMapper.selectOne(new LambdaQueryWrapper<NotificationBroadcastJob>()
                .eq(NotificationBroadcastJob::getJobNo, jobNo)
                .last("LIMIT 1"));
        if (job == null) {
            throw new BusinessException("BROADCAST_JOB_NOT_FOUND", "群发批次不存在");
        }
        List<NotificationBroadcastItem> failedItems = itemMapper.selectList(
                new LambdaQueryWrapper<NotificationBroadcastItem>()
                        .eq(NotificationBroadcastItem::getJobId, job.getId())
                        .eq(NotificationBroadcastItem::getStatus, "FAILED"));
        for (NotificationBroadcastItem failedItem : failedItems) {
            if (itemMapper.markDelivering(job.getId(), failedItem.getUserId()) == 0) {
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Notification notification = buildNotification(failedItem.getUserId(),
                            job.getType(), job.getTitle(), job.getContent());
                    notificationMapper.insert(notification);
                    itemMapper.markDelivered(failedItem.getId(), notification.getId());
                });
                pushQuietly(failedItem.getUserId(), requireNotification(failedItem.getUserId(), job));
            } catch (Exception e) {
                String error = String.valueOf(e.getMessage());
                itemMapper.markFailedAgain(failedItem.getId(),
                        error.substring(0, Math.min(255, error.length())));
                log.warn("[ADMIN03] 重试仍失败 jobNo={}, userId={}: {}", jobNo, failedItem.getUserId(), e.getMessage());
            }
        }
        // 计数以明细为权威事实重算（CAS 竞态下循环内计数不可靠）
        int delivered = countItems(job.getId(), "DELIVERED");
        int failed = countItems(job.getId(), "FAILED");
        int skipped = countItems(job.getId(), "SKIPPED");
        job.setDeliveredCount(delivered);
        job.setFailedCount(failed);
        job.setSkippedCount(skipped);
        job.setTargetCount(delivered + failed + skipped);
        job.setStatus(failed == 0 ? "COMPLETED" : delivered > 0 ? "PARTIAL" : "FAILED");
        jobMapper.updateById(job);
        return toSummary(job);
    }

    /** 批次列表（时间倒序，offset 分页） */
    public List<BroadcastJobSummary> list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page - 1, 0) * safeSize;
        return jobMapper.selectList(new LambdaQueryWrapper<NotificationBroadcastJob>()
                        .orderByDesc(NotificationBroadcastJob::getId)
                        .last("LIMIT " + safeSize + " OFFSET " + offset))
                .stream().map(this::toSummary).toList();
    }

    /** 批次详情（含明细，上限 500 行） */
    public BroadcastJobDetail detail(String jobNo) {
        NotificationBroadcastJob job = jobMapper.selectOne(new LambdaQueryWrapper<NotificationBroadcastJob>()
                .eq(NotificationBroadcastJob::getJobNo, jobNo)
                .last("LIMIT 1"));
        if (job == null) {
            throw new BusinessException("BROADCAST_JOB_NOT_FOUND", "群发批次不存在");
        }
        List<BroadcastItemView> items = itemMapper.selectList(new LambdaQueryWrapper<NotificationBroadcastItem>()
                        .eq(NotificationBroadcastItem::getJobId, job.getId())
                        .orderByAsc(NotificationBroadcastItem::getId)
                        .last("LIMIT 500"))
                .stream()
                .map(item -> new BroadcastItemView(item.getUserId(), item.getStatus(),
                        item.getNotificationId(), item.getError(), item.getCreatedAt()))
                .toList();
        return new BroadcastJobDetail(toSummary(job), items);
    }

    // ==================== 内部 ====================

    private record UserRef(Long id, Integer status) {
    }

    /** 分页枚举全量会员（含状态，禁用账号由本服务记 SKIPPED） */
    private List<UserRef> enumerateUsers() {
        List<UserRef> users = new ArrayList<>();
        int page = 1;
        while (true) {
            ApiResponse<List<Map<String, Object>>> response = userFeignClient.listUsers(page, USER_PAGE_SIZE);
            if (response == null || !response.success() || response.data() == null) {
                throw new BusinessException("USER_SERVICE_ERROR", "获取用户列表失败，广播已取消");
            }
            for (Map<String, Object> record : response.data()) {
                Long id = Ids.parse(record.get("id"));
                if (id == null) {
                    continue;
                }
                Object status = record.get("status");
                users.add(new UserRef(id, status instanceof Number number ? number.intValue() : null));
            }
            if (response.data().size() < USER_PAGE_SIZE) {
                return users;
            }
            if (++page > MAX_PAGES) {
                throw new BusinessException("BROADCAST_SCALE_EXCEEDED", "用户规模超出广播上限，广播已取消");
            }
        }
    }

    private void insertItem(Long jobId, Long userId, String status, Long notificationId, String error) {
        NotificationBroadcastItem item = new NotificationBroadcastItem();
        item.setJobId(jobId);
        item.setUserId(userId);
        item.setStatus(status);
        item.setNotificationId(notificationId);
        item.setError(error);
        try {
            itemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            log.info("[ADMIN03] 明细已存在（幂等跳过） jobId={}, userId={}", jobId, userId);
        }
    }

    private void insertItemQuietly(Long jobId, Long userId, String status, String error) {
        try {
            transactionTemplate.executeWithoutResult(tx -> insertItem(jobId, userId, status, null, error));
        } catch (Exception insertError) {
            log.error("[ADMIN03] 失败明细登记异常 jobId={}, userId={}", jobId, userId, insertError);
        }
    }

    private int countItems(Long jobId, String status) {
        Long count = itemMapper.selectCount(new LambdaQueryWrapper<NotificationBroadcastItem>()
                .eq(NotificationBroadcastItem::getJobId, jobId)
                .eq(NotificationBroadcastItem::getStatus, status));
        return count == null ? 0 : count.intValue();
    }

    private Notification requireNotification(Long userId, NotificationBroadcastJob job) {
        Notification notification = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, job.getType())
                .eq(Notification::getTitle, job.getTitle())
                .orderByDesc(Notification::getId)
                .last("LIMIT 1"));
        if (notification == null) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "重试投递后通知缺失");
        }
        return notification;
    }

    private void pushQuietly(Long userId, Notification entity) {
        try {
            NotificationDTO dto = notificationConverter.toDTO(entity);
            sessionManager.sendMessageToUser(userId, dto);
        } catch (Exception e) {
            // 在线推送为尽力而为，落库事实已成立
            log.debug("[ADMIN03] 在线推送未送达 userId={}: {}", userId, e.getMessage());
        }
    }

    private Notification buildNotification(Long userId, String type, String title, String content) {
        Notification entity = new Notification();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setIsRead(0);
        return entity;
    }

    private String nextJobNo() {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(JOB_NO_FORMAT);
        return "BRD" + timestamp + String.format("%04d", RANDOM.nextInt(10000));
    }

    private BroadcastJobSummary toSummary(NotificationBroadcastJob job) {
        return new BroadcastJobSummary(job.getJobNo(), job.getType(), job.getTitle(),
                job.getTargetCount() == null ? 0 : job.getTargetCount(),
                job.getDeliveredCount() == null ? 0 : job.getDeliveredCount(),
                job.getFailedCount() == null ? 0 : job.getFailedCount(),
                job.getSkippedCount() == null ? 0 : job.getSkippedCount(),
                job.getStatus(), job.getCreatedBy(), job.getCreatedAt());
    }

}
