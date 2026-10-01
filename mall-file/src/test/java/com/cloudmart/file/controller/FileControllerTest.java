package com.cloudmart.file.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.service.FileAssetService;
import com.cloudmart.file.service.FileService;
import com.cloudmart.file.service.UploadQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S01/LC05：唯一资产上传入口——配额/内容校验/可见性分域/台账链路；
 * 旧 /upload 与 /delete?url= 通道已删除（旧路径无可执行 handler）。
 */
class FileControllerTest {

    private MockMvc mockMvc;

    private final FileService fileService = Mockito.mock(FileService.class);
    private final UploadQuotaService uploadQuotaService = Mockito.mock(UploadQuotaService.class);
    private final FileAssetService fileAssetService = Mockito.mock(FileAssetService.class);

    @BeforeEach
    void setUp() {
        // persist 需 sha256 参数；mock 默认返回 null 会绕过 anyString() 匹配
        given(fileAssetService.sha256Hex(any())).willReturn("a".repeat(64));
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, uploadQuotaService,
                fileAssetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", "image-content".getBytes());
    }

    private FileAsset asset(long id, String visibility) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setStorageKey(visibility + "/pic/20261001/test" + id + ".jpg");
        asset.setMime("image/jpeg");
        asset.setSizeBytes(13L);
        asset.setVisibility(visibility);
        asset.setStatus("READY");
        return asset;
    }

    @Test
    @DisplayName("PUBLIC 上传 - 校验→分域落盘→台账→返回 /files 静态 URL 与 fileId")
    void uploadAsset_public_ReturnsStaticUrl() throws Exception {
        given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
        given(fileService.store(any(), anyString(), anyString())).willReturn("public/pic/20261001/a.jpg");
        given(fileService.publicUrlOf("public/pic/20261001/a.jpg")).willReturn("/files/pic/20261001/a.jpg");
        given(fileAssetService.persist(any(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                .willReturn(asset(42L, "PUBLIC"));

        mockMvc.perform(multipart("/assets").file(imageFile())
                        .param("visibility", "PUBLIC")
                        .header("X-User-Id", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value("42"))
                .andExpect(jsonPath("$.data.url").value("/files/pic/20261001/a.jpg"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.status").value("READY"));

        verify(uploadQuotaService).reserve("10001", "test.jpg");
        verify(uploadQuotaService, never()).refund(anyString(), anyString());
    }

    @Test
    @DisplayName("PRIVATE 上传 - 落私有域，url 为 null（无公开 URL），凭授权下载")
    void uploadAsset_private_NoPublicUrl() throws Exception {
        given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
        given(fileService.store(any(), anyString(), anyString())).willReturn("private/pic/20261001/a.jpg");
        given(fileService.publicUrlOf("private/pic/20261001/a.jpg")).willReturn(null);
        given(fileAssetService.persist(any(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                .willReturn(asset(43L, "PRIVATE"));

        mockMvc.perform(multipart("/assets").file(imageFile())
                        .param("visibility", "PRIVATE")
                        .header("X-User-Id", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value("43"))
                .andExpect(jsonPath("$.data.url").doesNotExist())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"));

        verify(fileService).store(any(), anyString(), eq("PRIVATE"));
    }

    @Test
    @DisplayName("S01 引用登记协议：删除走归属授权 + DELETING CAS + 按 storageKey 回收实物")
    void deleteAsset_authorized_RecyclesPhysicalFile() throws Exception {
        given(fileAssetService.authorizeDelete(42L, 10001L, false)).willReturn(asset(42L, "PUBLIC"));

        mockMvc.perform(delete("/assets/42").header("X-User-Id", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fileService).deleteByStorageKey("PUBLIC/pic/20261001/test42.jpg");
        verify(fileAssetService).markDeleted(42L);
    }

    @Nested
    @DisplayName("上传配额与认证")
    class QuotaAndAuthTests {

        @Test
        @DisplayName("未登录（无 X-User-Id 且非管理员）- 401 拒绝")
        void shouldRejectAnonymousUpload() throws Exception {
            mockMvc.perform(multipart("/assets").file(imageFile()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

            verify(fileAssetService, never()).validateContent(any(), anyString());
        }

        @Test
        @DisplayName("用户当日配额超限 - 429 返回 UPLOAD_DAILY_LIMIT_EXCEEDED，不触碰存储")
        void shouldReturn429WhenQuotaExceeded() throws Exception {
            given(uploadQuotaService.reserve(anyString(), anyString()))
                    .willThrow(new BusinessException("UPLOAD_DAILY_LIMIT_EXCEEDED", "今日图片上传已达上限（30 个），请明天再试"));

            mockMvc.perform(multipart("/assets").file(imageFile())
                            .header("X-User-Id", "10001"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("UPLOAD_DAILY_LIMIT_EXCEEDED"));

            verify(fileService, never()).store(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("管理员（X-Admin-Role: admin）- 豁免配额直接上传")
        void shouldExemptAdminFromQuota() throws Exception {
            given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
            given(fileService.store(any(), anyString(), anyString())).willReturn("public/pic/20261001/b.jpg");
            given(fileService.publicUrlOf(anyString())).willReturn("/files/pic/20261001/b.jpg");
            given(fileAssetService.persist(any(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(asset(44L, "PUBLIC"));

            mockMvc.perform(multipart("/assets").file(imageFile())
                            .param("visibility", "PUBLIC")
                            .header("X-Admin-Role", "admin"))
                    .andExpect(status().isOk());

            verify(uploadQuotaService, never()).reserve(anyString(), anyString());
        }

        @Test
        @DisplayName("内容校验失败 - 配额归还，不落盘（校验先于存储）")
        void shouldRefundQuotaWhenValidationFails() throws Exception {
            given(uploadQuotaService.reserve(anyString(), anyString())).willReturn(true);
            willAnswer(invocation -> {
                throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "文件内容与扩展名不符");
            }).given(fileAssetService).validateContent(any(), anyString());

            mockMvc.perform(multipart("/assets").file(imageFile())
                            .header("X-User-Id", "10001"))
                    .andExpect(status().isBadRequest());

            verify(uploadQuotaService).refund("10001", "test.jpg");
            verify(fileService, never()).store(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("落盘失败 - 清理半成品 + 配额归还")
        void shouldCleanupAndRefundWhenStoreFails() throws Exception {
            given(uploadQuotaService.reserve(anyString(), anyString())).willReturn(true);
            given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
            willAnswer(invocation -> {
                throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败");
            }).given(fileService).store(any(), anyString(), anyString());

            mockMvc.perform(multipart("/assets").file(imageFile())
                            .header("X-User-Id", "10001"))
                    .andExpect(status().isBadRequest());

            verify(uploadQuotaService).refund("10001", "test.jpg");
        }
    }
}
