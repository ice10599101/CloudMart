package com.cloudmart.file.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.entity.FileReference;
import com.cloudmart.file.repository.FileAssetMapper;
import com.cloudmart.file.repository.FileReferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FILE-01：文件资产台账——魔数内容校验（SVG 拒绝/扩展名伪装拒绝）、归属删除、
 * 引用计数 409、私有附件短期签名下载（HMAC，过期/篡改拒绝）。
 */
@DisplayName("FileAssetService 文件资产台账")
class FileAssetServiceTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] SVG_TEXT = "<svg xmlns='http://www.w3.org/2000/svg'></svg>"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final byte[] HTML_TEXT = "<html><script>alert(1)</script></html>"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private FileAssetService service;
    private FileAssetMapper fileAssetMapper;
    private FileReferenceMapper fileReferenceMapper;

    @BeforeEach
    void setUp() {
        fileAssetMapper = mock(FileAssetMapper.class);
        fileReferenceMapper = mock(FileReferenceMapper.class);
        service = new FileAssetService(fileAssetMapper, fileReferenceMapper, "test-signing-secret");
    }

    private FileAsset ownedAsset(Long ownerId) {
        FileAsset asset = new FileAsset();
        asset.setId(100L);
        asset.setOwnerId(ownerId);
        asset.setStorageKey("pic/20260928/abc.png");
        asset.setMime("image/png");
        asset.setSizeBytes(13L);
        asset.setVisibility("PRIVATE");
        asset.setStatus("READY");
        return asset;
    }

    @Test
    @DisplayName("S01 校验/登记分离：PNG 校验通过 → persist 可见性一次写定，指纹入库")
    void validateAndPersist_png_ok() {
        String mime = service.validateContent(PNG_MAGIC, "photo.png");

        service.persist(42L, "photo.png", "private/pic/20260928/abc.png", PNG_MAGIC.length, mime, "PRIVATE");

        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(fileAssetMapper).insert(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(42L);
        assertThat(captor.getValue().getMime()).isEqualTo("image/png");
        assertThat(captor.getValue().getVisibility()).isEqualTo("PRIVATE");
        assertThat(captor.getValue().getStatus()).isEqualTo("READY");
        assertThat(captor.getValue().getStorageKey()).startsWith("private/");
    }

    @Test
    @DisplayName("SVG 一律拒绝（默认策略，防脚本内联）")
    void validateContent_svg_rejected() {
        assertThatThrownBy(() -> service.validateContent(SVG_TEXT, "icon.svg"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_TYPE_NOT_ALLOWED");
        verify(fileAssetMapper, never()).insert(any(FileAsset.class));
    }

    @Test
    @DisplayName("扩展名伪装拒绝：声明 .png 实为文本/HTML")
    void validateContent_disguisedExtension_rejected() {
        assertThatThrownBy(() -> service.validateContent(HTML_TEXT, "evil.png"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("归属者可删除；无引用时物理文件删除放行")
    void authorizeDelete_owner_ok() {
        when(fileAssetMapper.selectById(100L)).thenReturn(ownedAsset(42L));
        when(fileReferenceMapper.selectCount(any())).thenReturn(0L);
        when(fileAssetMapper.markDeleting(100L)).thenReturn(1);

        FileAsset asset = service.authorizeDelete(100L, 42L, false);

        assertThat(asset.getId()).isEqualTo(100L);
        assertThat(asset.getStatus()).isEqualTo("DELETING");
    }

    @Test
    @DisplayName("非归属者删除被拒（404/403 语义，绝不以 URL 决定删除对象）")
    void authorizeDelete_nonOwner_forbidden() {
        when(fileAssetMapper.selectById(100L)).thenReturn(ownedAsset(42L));

        assertThatThrownBy(() -> service.authorizeDelete(100L, 99L, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_FORBIDDEN");
    }

    @Test
    @DisplayName("无主（LEGACY_UNCLAIMED 类）资产仅管理员可删")
    void authorizeDelete_unclaimed_adminOnly() {
        when(fileAssetMapper.selectById(100L)).thenReturn(ownedAsset(null));
        when(fileAssetMapper.markDeleting(100L)).thenReturn(1);

        assertThatThrownBy(() -> service.authorizeDelete(100L, 42L, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_FORBIDDEN");

        FileAsset asset = service.authorizeDelete(100L, 42L, true);
        assertThat(asset).isNotNull();
    }

    @Test
    @DisplayName("被业务引用的文件删除返回 409 FILE_REFERENCED")
    void authorizeDelete_referenced_conflict() {
        when(fileAssetMapper.selectById(100L)).thenReturn(ownedAsset(42L));
        when(fileReferenceMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.authorizeDelete(100L, 42L, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_REFERENCED");
    }

    @Test
    @DisplayName("S01 竞态关闭：CAS READY→DELETING 0 行（并发删除/已推进）→ FILE_DELETE_CONFLICT")
    void authorizeDelete_casLost_conflict() {
        when(fileAssetMapper.selectById(100L)).thenReturn(ownedAsset(42L));
        when(fileReferenceMapper.selectCount(any())).thenReturn(0L);
        when(fileAssetMapper.markDeleting(100L)).thenReturn(0);

        assertThatThrownBy(() -> service.authorizeDelete(100L, 42L, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_DELETE_CONFLICT");
    }

    @Test
    @DisplayName("私有附件：归属者可取签名 URL，他人被拒")
    void buildDownloadPath_private_ownerOnly() {
        when(fileAssetMapper.selectById(100L)).thenReturn(ownedAsset(42L));

        String path = service.buildDownloadPath(100L, 42L, false, Instant.now());
        assertThat(path).contains("/file/assets/100/download").contains("token=").contains("expires=");

        assertThatThrownBy(() -> service.buildDownloadPath(100L, 99L, false, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_FORBIDDEN");

        // 管理员可审计访问
        assertThat(service.buildDownloadPath(100L, 99L, true, Instant.now())).isNotBlank();
    }

    @Test
    @DisplayName("签名下载：有效签名通过，过期/篡改拒绝；签名服务未配置 fail-closed")
    void validateDownloadToken() {
        when(fileAssetMapper.selectById(100L)).thenReturn(ownedAsset(42L));
        String path = service.buildDownloadPath(100L, 42L, false, Instant.now());
        String tokenValue = path.substring(path.indexOf("token=") + 6, path.indexOf("&expires"));
        String expiresValue = path.substring(path.indexOf("&expires=") + "&expires=".length());

        service.validateDownloadToken(100L, tokenValue, expiresValue);

        // 篡改
        assertThatThrownBy(() -> service.validateDownloadToken(100L, "bad" + tokenValue, expiresValue))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_FORBIDDEN");

        // 过期：签发时即已过期（now-620 + 600s < now）
        String expiredPath = service.buildDownloadPath(100L, 42L, false, Instant.now().minusSeconds(620));
        String expiredToken = expiredPath.substring(expiredPath.indexOf("token=") + 6, expiredPath.indexOf("&expires"));
        String expiredExpires = expiredPath.substring(expiredPath.indexOf("&expires=") + "&expires=".length());
        assertThatThrownBy(() -> service.validateDownloadToken(100L, expiredToken, expiredExpires))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_FORBIDDEN");

        // 未配置签名密钥 fail-closed
        FileAssetService unsigned = new FileAssetService(fileAssetMapper, fileReferenceMapper, "");
        assertThatThrownBy(() -> unsigned.validateDownloadToken(100L, tokenValue, expiresValue))
                .isInstanceOf(BusinessException.class);
    }
}
