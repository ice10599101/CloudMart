package com.cloudmart.file.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.service.FileAssetService;
import com.cloudmart.file.service.FileDeletionService;
import com.cloudmart.file.service.FileService;
import com.cloudmart.file.service.UploadQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

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
    private final FileDeletionService fileDeletionService = Mockito.mock(FileDeletionService.class);

    @BeforeEach
    void setUp() {
        // persist 需 sha256 参数；mock 默认返回 null 会绕过 anyString() 匹配
        given(fileAssetService.sha256Hex(any())).willReturn("a".repeat(64));
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, uploadQuotaService,
                fileAssetService, fileDeletionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** T04：管理员身份来自已验签上下文，不再由请求头决定。 */
    private void authenticateAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        userId, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", "image-content".getBytes());
    }

    @Test
    void missingOrEmptyFileDoesNotConsumeQuota() throws Exception {
        mockMvc.perform(multipart("/assets").header("X-User-Id", "10001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_EMPTY"));
        mockMvc.perform(multipart("/assets")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header("X-User-Id", "10001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_EMPTY"));
        verify(uploadQuotaService, never()).reserve(anyString(), anyString());
        verify(fileService, never()).store(any(), anyString(), anyString());
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
        given(fileAssetService.persist(any(), anyString(), any(), any(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString()))
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
        given(fileAssetService.persist(any(), anyString(), any(), any(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString()))
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
    @DisplayName("S01 引用登记协议：删除走 FileDeletionService 编排（归属授权 + CAS + 实物回收）")
    void deleteAsset_authorized_RecyclesPhysicalFile() throws Exception {
        mockMvc.perform(delete("/assets/42").header("X-User-Id", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fileDeletionService).delete(42L, 10001L, false);
    }

    @Test
    @DisplayName("ADMIN-04：管理员上传必须声明用途，台账记录 ADMIN 域与操作者")
    void uploadAsset_admin_requiresPurposeAndRecordsOperator() throws Exception {
        authenticateAs("9001", "ROLE_ADMIN");
        given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
        given(fileService.store(any(), anyString(), anyString())).willReturn("public/pic/20261017/a.jpg");
        given(fileService.publicUrlOf("public/pic/20261017/a.jpg")).willReturn("/files/pic/20261017/a.jpg");
        given(fileAssetService.persist(any(), anyString(), any(), any(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString()))
                .willReturn(asset(50L, "PUBLIC"));

        // 缺 purpose → 400（不得静默归入管理员域）
        mockMvc.perform(multipart("/assets").file(imageFile())
                        .param("visibility", "PUBLIC")
                        .header("X-User-Id", "9001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_PURPOSE_REQUIRED"));

        // 带 purpose → 成功，ownerType=ADMIN、createdBy=管理员账号
        mockMvc.perform(multipart("/assets").file(imageFile())
                        .param("visibility", "PUBLIC")
                        .param("purpose", "GIFT_IMAGE")
                        .header("X-User-Id", "9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value("50"));

        verify(fileAssetService).persist(any(), eq("ADMIN"), eq(9001L), eq("GIFT_IMAGE"),
                anyString(), anyString(), anyLong(), anyString(), anyString(), eq("PUBLIC"));
    }

    @Test
    @DisplayName("T04：普通用户直连带伪造 X-Admin-Role 头不得获得管理员语义")
    void uploadAsset_userWithForgedAdminHeader_isNotAdmin() throws Exception {
        authenticateAs("10001", "ROLE_USER");
        given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
        given(fileService.store(any(), anyString(), anyString())).willReturn("public/pic/20261017/u.jpg");
        given(fileService.publicUrlOf(anyString())).willReturn("/files/pic/20261017/u.jpg");
        given(fileAssetService.persist(any(), anyString(), any(), any(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString()))
                .willReturn(asset(60L, "PUBLIC"));

        // 用户域上传：不要求 purpose、占用配额、ownerType=USER
        mockMvc.perform(multipart("/assets").file(imageFile())
                        .param("visibility", "PUBLIC")
                        .header("X-User-Id", "10001")
                        .header("X-Admin-Role", "admin"))
                .andExpect(status().isOk());

        verify(uploadQuotaService).reserve("10001", "test.jpg");
        verify(fileAssetService).persist(any(), eq("USER"), eq(10001L), any(),
                anyString(), anyString(), anyLong(), anyString(), anyString(), eq("PUBLIC"));
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
        @DisplayName("管理员（已验签 ROLE_ADMIN）- 豁免配额直接上传")
        void shouldExemptAdminFromQuota() throws Exception {
            authenticateAs("9001", "ROLE_ADMIN");
            given(fileAssetService.validateContent(any(), anyString())).willReturn("image/jpeg");
            given(fileService.store(any(), anyString(), anyString())).willReturn("public/pic/20261001/b.jpg");
            given(fileService.publicUrlOf(anyString())).willReturn("/files/pic/20261001/b.jpg");
            given(fileAssetService.persist(any(), anyString(), any(), any(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString()))
                    .willReturn(asset(44L, "PUBLIC"));

            // ADMIN-04：管理员上传必须声明业务用途（新契约），此处沿用免配额验证
            mockMvc.perform(multipart("/assets").file(imageFile())
                            .param("visibility", "PUBLIC")
                            .param("purpose", "ADMIN_ASSET")
                            .header("X-User-Id", "9001"))
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
