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
 * 用户数据导出服务实现（合规 34.2，四AB B5）。
 *
 * <p>自包含链路：PENDING → 单线程执行器异步聚合用户数据（心愿/成长/还愿/
 * 互动/收藏/统计）生成 JSON 落库 → SUCCESS（7 天有效期）→ 下载端点流式输出。
 * 内容直接落库而非 OSS：用户个人数据量级小（MB 级内），避免跨服务文件依赖；
 * download_url 字段保留给未来 OSS 化。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportServiceImpl implements DataExportService {

    private static final long EXPIRE_DAYS = 7;

    private final DataExportMapper exportMapper;
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
                final List<DataExport> stuck = exportMapper.selectList(
                        new LambdaQueryWrapper<DataExport>().eq(DataExport::getStatus, "PENDING"));
                for (DataExport task : stuck) {
                    log.info("恢复遗留导出任务 taskId={} userId={}", task.getId(), task.getUserId());
                    exportExecutor.execute(() -> generate(task.getId(), task.getUserId()));
                }
            } catch (Exception ex) {
                log.error("恢复遗留导出任务失败", ex);
            }
        });
    }

    @Override
    public DataExport createExport(Long userId) {
        final DataExport export = new DataExport();
        export.setUserId(userId);
        export.setStatus("PENDING");
        export.setExpiresAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(EXPIRE_DAYS));
        exportMapper.insert(export);

        final Long taskId = export.getId();
        exportExecutor.execute(() -> generate(taskId, userId));
        return export;
    }

    /** 异步生成：PROCESSING → 聚合 JSON 落库 → SUCCESS/FAILED */
    @SuppressWarnings("unchecked")
    private void generate(Long taskId, Long userId) {
        try {
            final DataExport task = exportMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            task.setStatus("PROCESSING");
            exportMapper.updateById(task);

            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("exportedAt", LocalDateTime.now(ZoneId.of("UTC")).toString());
            payload.put("stat", userStatMapper.selectOne(
                    new LambdaQueryWrapper<WishUserStat>().eq(WishUserStat::getUserId, userId)));
            payload.put("wishes", wishMapper.selectList(
                    new LambdaQueryWrapper<Wish>().eq(Wish::getUserId, userId)));
            List<WishGrowthRecord> exportRecords = growthRecordMapper.selectList(
                    new LambdaQueryWrapper<WishGrowthRecord>().eq(WishGrowthRecord::getUserId, userId));
            exportRecords.forEach(r -> r.setContent(contentCipher.decryptGrowth(
                    GrowthRecordType.DIARY == r.getType(), r.getContent())));
            payload.put("growthRecords", exportRecords);
            payload.put("fulfillments", fulfillmentMapper.selectList(
                    new LambdaQueryWrapper<WishFulfillment>().eq(WishFulfillment::getUserId, userId)));
            payload.put("interactions", interactionMapper.selectList(
                    new LambdaQueryWrapper<WishInteraction>().eq(WishInteraction::getUserId, userId)));
            payload.put("collections", collectionMapper.selectList(
                    new LambdaQueryWrapper<WishCollection>().eq(WishCollection::getUserId, userId)));

            final DataExport update = new DataExport();
            update.setId(taskId);
            update.setStatus("SUCCESS");
            update.setContent(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
            update.setExpiresAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(EXPIRE_DAYS));
            exportMapper.updateById(update);
        } catch (Exception ex) {
            log.error("数据导出任务失败 taskId={}", taskId, ex);
            final DataExport failed = new DataExport();
            failed.setId(taskId);
            failed.setStatus("FAILED");
            exportMapper.updateById(failed);
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
        if (!"SUCCESS".equals(task.getStatus()) || task.getContent() == null) {
            return null;
        }
        if (task.getExpiresAt() != null && task.getExpiresAt().isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
            // 惰性过期：内容清空并置 FAILED
            final DataExport expired = new DataExport();
            expired.setId(taskId);
            expired.setStatus("FAILED");
            expired.setContent(null);
            exportMapper.updateById(expired);
            return null;
        }
        return task.getContent();
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
                .isNotNull(DataExport::getContent)
                .lt(DataExport::getExpiresAt, LocalDateTime.now(ZoneId.of("UTC"))));
        for (final DataExport task : expired) {
            final DataExport update = new DataExport();
            update.setId(task.getId());
            update.setStatus("FAILED");
            update.setContent(null);
            exportMapper.updateById(update);
        }
        if (!expired.isEmpty()) {
            log.info("已清理过期导出内容 {} 条", expired.size());
        }
    }
}
