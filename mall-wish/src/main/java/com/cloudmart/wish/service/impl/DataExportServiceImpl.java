package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.DataExport;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCollection;
import com.cloudmart.wish.entity.WishFulfillment;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.enums.GrowthRecordType;
import com.cloudmart.wish.util.ContentCipher;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.repository.DataExportMapper;
import com.cloudmart.wish.repository.WishCollectionMapper;
import com.cloudmart.wish.repository.WishFulfillmentMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.DataExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户数据导出服务实现（合规 34.2 / W04）。
 *
 * <p>链路：PENDING → 租约认领（CAS，多实例单执行者）→ 异步聚合用户数据
 * （心愿/成长/还愿/互动/收藏/统计）→ <b>整体 AES-GCM 加密后落 content_enc</b>
 * （ContentCipher v2 信封，AAD 绑定 taskId；<b>明文不落库</b>——DB 备份中无
 * DIARY 明文副本）→ SUCCESS（7 天有效期）→ 下载端点解密流式输出。</p>
 *
 * <p>W04 保障：</p>
 * <ul>
 *   <li>租约：PROCESSING 持 lease_owner/lease_until；恢复扫描只接管租约过期的
 *       任务（QA31：两实例不重复处理、进程重启续作）；</li>
 *   <li>有界并发：进程内单线程执行器 + DB 状态机，无界聚合消除；</li>
 *   <li>密钥与文件分离：加密密钥来自应用配置（WishCryptoProperties），不随
 *       导出内容存储；下载需归属校验 + 未过期（审计走 OperLog 由控制器承担）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportServiceImpl implements DataExportService {

    private static final long EXPIRE_DAYS = 7;
    /** 租约时长：聚合为秒级操作，超时即视为执行者丢失 */
    private static final long LEASE_MINUTES = 10;
    /** 加密 AAD 域（防跨任务密文挪用） */
    private static final String EXPORT_AAD_PREFIX = "EXPORT:";



    private final DataExportMapper exportMapper;
    private final java.util.concurrent.atomic.AtomicLong leaseSeq = new java.util.concurrent.atomic.AtomicLong();
    private final ContentCipher contentCipher;
    private final WishMapper wishMapper;
    private final WishGrowthRecordMapper growthRecordMapper;
    private final WishFulfillmentMapper fulfillmentMapper;
    private final WishInteractionMapper interactionMapper;
    private final WishCollectionMapper collectionMapper;
    private final WishUserStatMapper userStatMapper;
    private final ObjectMapper objectMapper;

    /** 单线程执行器：导出任务串行化，避免并发聚合压库 */
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "wish-data-export");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * PENDING 任务恢复：内存执行器队列在实例重启时会丢任务（DB 状态停在 PENDING），
     * 启动后延迟扫描一次，把遗留 PENDING 任务重新入队，保证合规导出最终可完成。
     */
    @PostConstruct
    public void recoverPendingTasks() {
        exportExecutor.execute(() -> {
            try {
                // W04：只恢复 PENDING + 租约过期的 PROCESSING——活跃租约归其他实例所有，
                // 不重复处理（QA31：多实例各恢复各的，无重复执行）
                final List<DataExport> stuck = exportMapper.selectList(
                        new LambdaQueryWrapper<DataExport>()
                                .and(w -> w.eq(DataExport::getStatus, "PENDING")
                                        .or(x -> x.eq(DataExport::getStatus, "PROCESSING")
                                                .isNotNull(DataExport::getLeaseUntil)
                                                .lt(DataExport::getLeaseUntil,
                                                        LocalDateTime.now(ZoneId.of("UTC")))))
                                .last("LIMIT 50"));
                for (DataExport task : stuck) {
                    log.info("恢复遗留导出任务 taskId={} userId={} status={}",
                            task.getId(), task.getUserId(), task.getStatus());
                    exportExecutor.execute(() -> generate(task.getId(), task.getUserId()));
                }
            } catch (Exception ex) {
                log.error("恢复遗留导出任务失败", ex);
            }
        });
    }

    /** W04：租约持有者标识（实例进程 + 提交序号，接管时轮换） */
    private String leaseOwner() {  // fully-qualified below
        return java.lang.management.ManagementFactory.getRuntimeMXBean().getName() + ":exp" + leaseSeq.incrementAndGet();
    }

    private LocalDateTime leaseUntil() {
        return LocalDateTime.now(ZoneId.of("UTC")).plusMinutes(LEASE_MINUTES);
    }

    @Override
    public DataExport createExport(Long userId) {
        // B19：每用户同时仅一个进行中任务；24 小时内最多 2 次
        Long active = exportMapper.selectCount(new LambdaQueryWrapper<DataExport>()
                .eq(DataExport::getUserId, userId)
                .in(DataExport::getStatus, "PENDING", "PROCESSING"));
        if (active != null && active > 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "已有进行中的导出任务");
        }
        Long recent = exportMapper.selectCount(new LambdaQueryWrapper<DataExport>()
                .eq(DataExport::getUserId, userId)
                .ge(DataExport::getCreatedAt, LocalDateTime.now(ZoneId.of("UTC")).minusHours(24)));
        if (recent != null && recent >= 2) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "24 小时内导出次数已达上限");
        }
        final DataExport export = new DataExport();
        export.setUserId(userId);
        export.setStatus("PENDING");
        export.setExpiresAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(EXPIRE_DAYS));
        exportMapper.insert(export);

        final Long taskId = export.getId();
        exportExecutor.execute(() -> generate(taskId, userId));
        return export;
    }

    /**
     * 异步生成：租约认领（CAS）→ 聚合 JSON → 整体加密落 content_enc → SUCCESS。
     * 明文仅在内存中存在，落库前已加密（W04：DB 备份无 DIARY 明文副本）。
     */
    @SuppressWarnings("unchecked")
    private void generate(Long taskId, Long userId) {
        final String owner = leaseOwner();
        // W04：租约认领——PENDING 直接认领；PROCESSING 需接管过期租约（前执行者崩溃）
        int claimed = exportMapper.claimPending(taskId, owner, leaseUntil());
        if (claimed == 0) {
            claimed = exportMapper.takeoverExpiredLease(taskId, owner, leaseUntil());
        }
        if (claimed == 0) {
            log.info("导出任务由其他实例持有，跳过 taskId={}", taskId);
            return;
        }
        try {
            final DataExport task = exportMapper.selectById(taskId);
            if (task == null) {
                return;
            }

            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("exportedAt", LocalDateTime.now(ZoneId.of("UTC")).toString());
            payload.put("stat", userStatMapper.selectOne(
                    new LambdaQueryWrapper<WishUserStat>().eq(WishUserStat::getUserId, userId)));
            payload.put("wishes", wishMapper.selectList(
                    new LambdaQueryWrapper<Wish>().eq(Wish::getUserId, userId)));
            List<WishGrowthRecord> exportRecords = growthRecordMapper.selectList(
                    new LambdaQueryWrapper<WishGrowthRecord>().eq(WishGrowthRecord::getUserId, userId));
            exportRecords.forEach(r -> r.setContent(contentCipher.decryptGrowth(
                    GrowthRecordType.DIARY == r.getType(),
                    "GROWTH:" + r.getWishId() + ":" + r.getUserId(), r.getContent())));
            payload.put("growthRecords", exportRecords);
            payload.put("fulfillments", fulfillmentMapper.selectList(
                    new LambdaQueryWrapper<WishFulfillment>().eq(WishFulfillment::getUserId, userId)));
            payload.put("interactions", interactionMapper.selectList(
                    new LambdaQueryWrapper<WishInteraction>().eq(WishInteraction::getUserId, userId)));
            payload.put("collections", collectionMapper.selectList(
                    new LambdaQueryWrapper<WishCollection>().eq(WishCollection::getUserId, userId)));

            final String plain = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
            // W04：整体加密（AAD 绑定任务身份，防密文挪用）；明文不落库
            final String encrypted = contentCipher.encrypt(plain,
                    EXPORT_AAD_PREFIX + taskId + ":" + userId);
            final String cipherSha = sha256Hex(encrypted);

            final DataExport update = new DataExport();
            update.setId(taskId);
            update.setStatus("SUCCESS");
            update.setContentEnc(encrypted);
            update.setContentSha256(cipherSha);
            update.setExpiresAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(EXPIRE_DAYS));
            exportMapper.updateById(update);
            exportMapper.clearLease(taskId);
            log.info("数据导出完成（已加密落库） taskId={}, cipherSize={}", taskId, encrypted.length());
        } catch (Exception ex) {
            log.error("数据导出任务失败 taskId={}", taskId, ex);
            final DataExport failed = new DataExport();
            failed.setId(taskId);
            failed.setStatus("FAILED");
            exportMapper.updateById(failed);
            exportMapper.clearLease(taskId);
        }
    }

    private String sha256Hex(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public String loadContent(Long userId, Long taskId) {
        purgeExpired();
        final DataExport task = exportMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            // 归属校验：他人任务视为不存在
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "导出任务不存在");
        }
        if (!"SUCCESS".equals(task.getStatus()) || task.getContentEnc() == null) {
            return null;
        }
        if (task.getExpiresAt() != null && task.getExpiresAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
            // 惰性过期：密文清空并置 FAILED
            clearContent(taskId);
            return null;
        }
        // W04：完整性校验 + 授权解密（密文/密钥分离存储；下载即授权输出）
        if (task.getContentSha256() != null
                && !task.getContentSha256().equals(sha256Hex(task.getContentEnc()))) {
            log.error("导出密文完整性校验失败 taskId={}", taskId);
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "导出内容校验失败，请重新导出");
        }
        return contentCipher.decrypt(task.getContentEnc(),
                EXPORT_AAD_PREFIX + task.getId() + ":" + task.getUserId());
    }

    @Override
    public DataExport getTask(Long userId, Long taskId) {
        purgeExpired();
        final DataExport task = exportMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "导出任务不存在");
        }
        return task;
    }

    @Override
    public java.util.List<DataExport> listTasks(Long userId) {
        purgeExpired();
        return exportMapper.selectList(new LambdaQueryWrapper<DataExport>()
                .eq(DataExport::getUserId, userId)
                .orderByDesc(DataExport::getId));
    }

    @Override
    public void purgeExpired() {
        final var expired = exportMapper.selectList(new LambdaQueryWrapper<DataExport>()
                .eq(DataExport::getStatus, "SUCCESS")
                .and(w -> w.isNotNull(DataExport::getContent).or().isNotNull(DataExport::getContentEnc))
                .lt(DataExport::getExpiresAt, LocalDateTime.now(ZoneId.of("UTC"))));
        for (final DataExport task : expired) {
            clearContent(task.getId());
        }
        if (!expired.isEmpty()) {
            log.info("已清理过期导出内容 {} 条", expired.size());
        }
    }

    /** B19：显式 SET content=NULL（updateById 空字段不落库的缺陷），DB 与存储一致清空 */
    private void clearContent(Long taskId) {
        // W04：过期销毁——密文一并清空（不留可解密残留）
        exportMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DataExport>()
                .eq(DataExport::getId, taskId)
                .set(DataExport::getStatus, "FAILED")
                .set(DataExport::getContent, null)
                .set(DataExport::getContentEnc, null)
                .set(DataExport::getContentSha256, null));
    }
}
