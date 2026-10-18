package com.cloudmart.file.service;

import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.entity.FileOrphanReclaim;
import com.cloudmart.file.repository.FileOrphanReclaimMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * T27：文件删除编排与恢复——统一「物理删除 → 台账收口」的失败可恢复闭环。
 *
 * <ul>
 *   <li>用户/管理员删除：授权 + 状态流转后，物理删除（幂等）成功才落 DELETED；失败登记
 *       错误并退避重试，绝不把 DELETING 放回 READY 让新引用进入；</li>
 *   <li>恢复扫描：定时重试到期 DELETING 资产与 PENDING 孤儿回收义务；</li>
 *   <li>孤儿回收义务：上传失败清理实物也失败时登记 storageKey（白名单回收），不接受任意路径。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionService {

    private final FileAssetService fileAssetService;
    private final FileService fileService;
    private final FileOrphanReclaimMapper orphanReclaimMapper;

    /**
     * 删除资产（归属者或管理员）。
     * 已删除（{@code authorizeDelete} 返回 null）时幂等成功；物理删除失败时登记错误并上抛。
     */
    public void delete(Long fileId, Long requesterId, boolean isAdmin) {
        FileAsset asset = fileAssetService.authorizeDelete(fileId, requesterId, isAdmin);
        if (asset == null) {
            // 已删除，幂等成功
            return;
        }
        deletePhysicalAndFinalize(asset);
    }

    /** 管理员删除（无主资产场景 requesterId=null，isAdmin=true） */
    public void deleteAdmin(Long fileId) {
        delete(fileId, null, true);
    }

    /**
     * 物理删除并收口台账：成功 CAS→DELETED，失败登记错误+退避并上抛。
     * 物理删除幂等（MinIO removeObject / 本地 deleteIfExists 对不存在对象均成功），
     * 因此进程在「物理删除后、台账收口前」退出也能安全重试。
     */
    private void deletePhysicalAndFinalize(FileAsset asset) {
        try {
            fileService.deleteByStorageKey(asset.getStorageKey());
            fileAssetService.markDeleted(asset.getId());
        } catch (RuntimeException e) {
            fileAssetService.markDeleteFailed(asset.getId(), e.getMessage());
            throw e;
        }
    }

    /** 恢复扫描：重试到期的 DELETING 资产，返回本轮成功收口的数量（失败继续退避，不阻断后续） */
    public int recoverDeleting(int batchSize) {
        var due = fileAssetService.listDueDeleting(LocalDateTime.now(ZoneOffset.UTC), batchSize);
        int done = 0;
        for (FileAsset asset : due) {
            try {
                fileService.deleteByStorageKey(asset.getStorageKey());
                fileAssetService.markDeleted(asset.getId());
                done++;
            } catch (RuntimeException e) {
                fileAssetService.markDeleteFailed(asset.getId(), e.getMessage());
                log.warn("文件删除恢复失败, fileId={}, storageKey={}: {}", asset.getId(),
                        asset.getStorageKey(), e.getMessage());
            }
        }
        return done;
    }

    /**
     * 登记孤儿实物回收义务（上传失败清理实物也失败时调用）。
     * 同一 storageKey 只登记一次（唯一键幂等），回收只针对本表登记的键，不接受任意路径。
     */
    public void registerOrphan(String storageKey, String reason) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        FileOrphanReclaim reclaim = new FileOrphanReclaim();
        reclaim.setStorageKey(storageKey);
        reclaim.setReason(reason == null || reason.isBlank() ? "UPLOAD_CLEANUP_FAILED" : reason);
        reclaim.setStatus("PENDING");
        reclaim.setAttempts(0);
        try {
            orphanReclaimMapper.insert(reclaim);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 同一孤儿键已登记回收义务，幂等忽略
            log.debug("孤儿回收义务已存在, storageKey={}", storageKey);
        }
    }

    /** 恢复扫描：回收到期的 PENDING 孤儿实物，返回本轮回收成功数量 */
    public int recoverOrphans(int batchSize) {
        var due = orphanReclaimMapper.selectDuePending(LocalDateTime.now(ZoneOffset.UTC), batchSize);
        int done = 0;
        for (FileOrphanReclaim reclaim : due) {
            try {
                fileService.deleteByStorageKey(reclaim.getStorageKey());
                orphanReclaimMapper.markReclaimed(reclaim.getId());
                done++;
            } catch (RuntimeException e) {
                orphanReclaimMapper.markReclaimFailed(reclaim.getId(), e.getMessage());
                log.warn("孤儿实物回收失败, reclaimId={}, storageKey={}: {}", reclaim.getId(),
                        reclaim.getStorageKey(), e.getMessage());
            }
        }
        return done;
    }
}
