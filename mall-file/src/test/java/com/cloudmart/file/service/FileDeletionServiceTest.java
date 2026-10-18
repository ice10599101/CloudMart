package com.cloudmart.file.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.entity.FileOrphanReclaim;
import com.cloudmart.file.repository.FileOrphanReclaimMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T27：文件删除编排——物理删除幂等 + 失败登记退避 + 恢复扫描 + 孤儿回收义务。
 */
@DisplayName("FileDeletionService 删除编排与恢复")
class FileDeletionServiceTest {

    private FileDeletionService service;
    private FileAssetService fileAssetService;
    private FileService fileService;
    private FileOrphanReclaimMapper orphanMapper;

    @BeforeEach
    void setUp() {
        fileAssetService = mock(FileAssetService.class);
        fileService = mock(FileService.class);
        orphanMapper = mock(FileOrphanReclaimMapper.class);
        service = new FileDeletionService(fileAssetService, fileService, orphanMapper);
    }

    private FileAsset deletingAsset(long id, String storageKey) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setStorageKey(storageKey);
        asset.setStatus("DELETING");
        return asset;
    }

    @Test
    @DisplayName("删除成功：授权返回资产 → 物理删除 → CAS 收口")
    void delete_success() {
        FileAsset asset = deletingAsset(100L, "private/pic/20261018/a.png");
        when(fileAssetService.authorizeDelete(100L, 42L, false)).thenReturn(asset);

        service.delete(100L, 42L, false);

        verify(fileService).deleteByStorageKey("private/pic/20261018/a.png");
        verify(fileAssetService).markDeleted(100L);
        verify(fileAssetService, never()).markDeleteFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("已删除幂等：authorizeDelete 返回 null → 不做物理删除/收口")
    void delete_alreadyDeleted_idempotent() {
        when(fileAssetService.authorizeDelete(100L, 42L, false)).thenReturn(null);

        service.delete(100L, 42L, false);

        verify(fileService, never()).deleteByStorageKey(anyString());
        verify(fileAssetService, never()).markDeleted(anyLong());
    }

    @Test
    @DisplayName("物理删除失败：登记错误并上抛，不放回 READY")
    void delete_physicalFailure_marksFailedAndRethrows() {
        FileAsset asset = deletingAsset(100L, "private/pic/20261018/a.png");
        when(fileAssetService.authorizeDelete(100L, 42L, false)).thenReturn(asset);
        doThrow(new BusinessException("FILE_DELETE_FAILED", "minio down"))
                .when(fileService).deleteByStorageKey(anyString());

        assertThatThrownBy(() -> service.delete(100L, 42L, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_DELETE_FAILED");

        verify(fileAssetService).markDeleteFailed(eq(100L), eq("minio down"));
        verify(fileAssetService, never()).markDeleted(anyLong());
    }

    @Test
    @DisplayName("管理员删除走 deleteAdmin（requesterId=null、isAdmin=true）")
    void deleteAdmin_delegates() {
        FileAsset asset = deletingAsset(100L, "private/pic/20261018/a.png");
        when(fileAssetService.authorizeDelete(100L, null, true)).thenReturn(asset);

        service.deleteAdmin(100L);

        verify(fileAssetService).authorizeDelete(100L, null, true);
        verify(fileService).deleteByStorageKey("private/pic/20261018/a.png");
    }

    @Test
    @DisplayName("恢复扫描：到期 DELETING 资产逐个重试，单个失败不阻断后续")
    void recoverDeleting_retriesDue() {
        FileAsset ok = deletingAsset(1L, "private/pic/20261018/a.png");
        FileAsset bad = deletingAsset(2L, "private/pic/20261018/b.png");
        when(fileAssetService.listDueDeleting(any(), eq(100))).thenReturn(List.of(ok, bad));
        doThrow(new BusinessException("FILE_DELETE_FAILED", "down"))
                .when(fileService).deleteByStorageKey("private/pic/20261018/b.png");

        int done = service.recoverDeleting(100);

        assertThat(done).isEqualTo(1);
        verify(fileService).deleteByStorageKey("private/pic/20261018/a.png");
        verify(fileService).deleteByStorageKey("private/pic/20261018/b.png");
        verify(fileAssetService).markDeleted(1L);
        verify(fileAssetService, never()).markDeleted(2L);
        verify(fileAssetService).markDeleteFailed(eq(2L), eq("down"));
    }

    @Test
    @DisplayName("孤儿回收：登记幂等（唯一键冲突忽略），到期回收")
    void orphanRegisterAndRecover() {
        service.registerOrphan("public/pic/20261018/orphan.png", "UPLOAD_CLEANUP_FAILED");

        ArgumentCaptor<FileOrphanReclaim> captor = ArgumentCaptor.forClass(FileOrphanReclaim.class);
        verify(orphanMapper).insert(captor.capture());
        assertThat(captor.getValue().getStorageKey()).isEqualTo("public/pic/20261018/orphan.png");
        assertThat(captor.getValue().getReason()).isEqualTo("UPLOAD_CLEANUP_FAILED");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");

        // 唯一键冲突：幂等忽略，不抛异常
        doThrow(new DuplicateKeyException("dup")).when(orphanMapper).insert(any(FileOrphanReclaim.class));
        service.registerOrphan("public/pic/20261018/orphan.png", "UPLOAD_CLEANUP_FAILED");

        // 到期回收
        FileOrphanReclaim due = new FileOrphanReclaim();
        due.setId(9L);
        due.setStorageKey("public/pic/20261018/orphan.png");
        when(orphanMapper.selectDuePending(any(), eq(100))).thenReturn(List.of(due));

        int done = service.recoverOrphans(100);
        assertThat(done).isEqualTo(1);
        verify(fileService).deleteByStorageKey("public/pic/20261018/orphan.png");
        verify(orphanMapper).markReclaimed(9L);
    }
}
