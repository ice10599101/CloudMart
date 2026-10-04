package com.cloudmart.file.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.entity.FileReference;
import com.cloudmart.file.repository.FileReferenceMapper;
import com.cloudmart.file.service.FileAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R04 内部绑定接口测试：归属/READY/MIME/大小/可见性由文件服务台账裁决；
 * 幂等引用键重复绑定返回 alreadyBound=true；请求形态非法直接拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InternalFileController 绑定测试（R04）")
class InternalFileControllerTest {

    @Mock
    private FileAssetService fileAssetService;
    @Mock
    private com.cloudmart.file.repository.FileAssetMapper fileAssetMapper;
    @Mock
    private FileReferenceMapper fileReferenceMapper;

    private InternalFileController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalFileController(fileAssetService, fileAssetMapper, fileReferenceMapper);
    }

    @Test
    @DisplayName("绑定成功：约束全部满足，登记引用并返回资产事实摘要")
    void bindReferenceSuccess() {
        when(fileAssetMapper.selectByIdForUpdate(55L)).thenReturn(asset(55L, 100L, "image/jpeg", 1024L, "PRIVATE", "READY"));
        when(fileReferenceMapper.insert(any(FileReference.class))).thenReturn(1);

        var result = controller.bindReference(55L, request(100L)).data();

        assertThat(result.assetId()).isEqualTo("55");
        assertThat(result.alreadyBound()).isFalse();
        ArgumentCaptor<FileReference> captor = ArgumentCaptor.forClass(FileReference.class);
        verify(fileReferenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getBizType()).isEqualTo("PET_ALBUM");
        assertThat(captor.getValue().getBizId()).isEqualTo("9001");
    }

    @Test
    @DisplayName("幂等重试：同引用键 DuplicateKey → alreadyBound=true，仍返回成功")
    void bindReferenceIdempotent() {
        when(fileAssetMapper.selectByIdForUpdate(55L)).thenReturn(asset(55L, 100L, "image/jpeg", 1024L, "PRIVATE", "READY"));
        when(fileReferenceMapper.insert(any(FileReference.class)))
                .thenThrow(new DuplicateKeyException("uk_file_reference_biz"));

        var result = controller.bindReference(55L, request(100L)).data();

        assertThat(result.alreadyBound()).isTrue();
    }

    @Test
    @DisplayName("他人文件拒绝：ownerUserId 与台账归属不符 → FILE_FORBIDDEN")
    void bindForeignFileRejected() {
        when(fileAssetMapper.selectByIdForUpdate(55L)).thenReturn(asset(55L, 999L, "image/jpeg", 1024L, "PRIVATE", "READY"));

        assertThatThrownBy(() -> controller.bindReference(55L, request(100L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_FORBIDDEN");
    }

    @Test
    @DisplayName("约束裁决：MIME 不在白名单 / 超限 / 可见性不符 / 非 READY 分别拒绝")
    void bindConstraintViolations() {
        when(fileAssetMapper.selectByIdForUpdate(55L)).thenReturn(asset(55L, 100L, "application/pdf", 1024L, "PRIVATE", "READY"));
        assertThatThrownBy(() -> controller.bindReference(55L, request(100L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_TYPE_NOT_ALLOWED");

        when(fileAssetMapper.selectByIdForUpdate(55L)).thenReturn(asset(55L, 100L, "image/jpeg", 10L * 1024 * 1024, "PRIVATE", "READY"));
        assertThatThrownBy(() -> controller.bindReference(55L, request(100L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_TOO_LARGE");

        when(fileAssetMapper.selectByIdForUpdate(55L)).thenReturn(asset(55L, 100L, "image/jpeg", 1024L, "PUBLIC", "READY"));
        assertThatThrownBy(() -> controller.bindReference(55L, request(100L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_VISIBILITY_MISMATCH");

        when(fileAssetMapper.selectByIdForUpdate(55L)).thenReturn(asset(55L, 100L, "image/jpeg", 1024L, "PRIVATE", "DELETING"));
        assertThatThrownBy(() -> controller.bindReference(55L, request(100L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_NOT_READY");
    }

    @Test
    @DisplayName("请求形态：bizType 含小写/特殊字符 → FILE_REFERENCE_INVALID")
    void bindInvalidRequestRejected() {
        assertThatThrownBy(() -> controller.bindReference(55L,
                new InternalFileController.BindReferenceRequest(
                        "pet-album!", "9001", 100L, List.of("image/jpeg"), 5L * 1024 * 1024, "PRIVATE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_REFERENCE_INVALID");
    }

    @Test
    @DisplayName("解绑幂等：无引用也返回成功")
    void unbindIdempotent() {
        controller.unbindReference(55L, "PET_ALBUM", "9001");

        verify(fileReferenceMapper).delete(any());
    }

    private InternalFileController.BindReferenceRequest request(Long ownerUserId) {
        return new InternalFileController.BindReferenceRequest(
                "PET_ALBUM", "9001", ownerUserId,
                List.of("image/jpeg", "image/png", "image/webp"), 5L * 1024 * 1024, "PRIVATE");
    }

    private FileAsset asset(Long id, Long ownerId, String mime, Long size, String visibility, String status) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setOwnerId(ownerId);
        asset.setMime(mime);
        asset.setSizeBytes(size);
        asset.setVisibility(visibility);
        asset.setStatus(status);
        return asset;
    }
}
