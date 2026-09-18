package com.cloudmart.file.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private MockMvc mockMvc;

    private final FileService fileService = Mockito.mock(FileService.class);
    private final UploadQuotaService uploadQuotaService = Mockito.mock(UploadQuotaService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, uploadQuotaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", "image-content".getBytes());
    }

    @Test
    @DisplayName("上传文件 - 成功返回信封")
    void upload_ShouldReturnEnvelope() throws Exception {
        given(fileService.upload(Mockito.any())).willReturn("https://oss.cloudmart.com/test.jpg");

        mockMvc.perform(multipart("/upload").file(imageFile())
                        .header("X-User-Id", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://oss.cloudmart.com/test.jpg"))
                .andExpect(jsonPath("$.data.originalFilename").value("test.jpg"));

        verify(uploadQuotaService).reserve("10001", "test.jpg");
        verify(uploadQuotaService, never()).refund(anyString(), anyString());
    }

    @Test
    @DisplayName("删除文件 - 成功返回信封")
    void delete_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/delete")
                        .param("url", "https://oss.cloudmart.com/test.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fileService).delete("https://oss.cloudmart.com/test.jpg");
    }

    @Nested
    @DisplayName("上传配额与认证")
    class QuotaAndAuthTests {

        @Test
        @DisplayName("未登录（无 X-User-Id 且非管理员）- 401 拒绝")
        void shouldRejectAnonymousUpload() throws Exception {
            mockMvc.perform(multipart("/upload").file(imageFile()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

            verify(fileService, never()).upload(any());
        }

        @Test
        @DisplayName("用户当日配额超限 - 429 返回 UPLOAD_DAILY_LIMIT_EXCEEDED")
        void shouldReturn429WhenQuotaExceeded() throws Exception {
            given(uploadQuotaService.reserve(anyString(), anyString()))
                    .willThrow(new BusinessException("UPLOAD_DAILY_LIMIT_EXCEEDED", "今日图片上传已达上限（30 个），请明天再试"));

            mockMvc.perform(multipart("/upload").file(imageFile())
                            .header("X-User-Id", "10001"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("UPLOAD_DAILY_LIMIT_EXCEEDED"));

            verify(fileService, never()).upload(any());
        }

        @Test
        @DisplayName("管理员（X-Admin-Role: admin）- 豁免配额直接上传")
        void shouldExemptAdminFromQuota() throws Exception {
            given(fileService.upload(Mockito.any())).willReturn("/files/pic/20260918/test.jpg");

            mockMvc.perform(multipart("/upload").file(imageFile())
                            .header("X-Admin-Role", "admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(uploadQuotaService, never()).reserve(anyString(), anyString());
        }

        @Test
        @DisplayName("用户角色 scope=user - 不视为管理员，仍受限额约束")
        void shouldNotTreatUserRoleAsAdmin() throws Exception {
            given(fileService.upload(Mockito.any())).willReturn("/files/pic/20260918/test.jpg");

            mockMvc.perform(multipart("/upload").file(imageFile())
                            .header("X-User-Id", "10001")
                            .header("X-Admin-Role", "user"))
                    .andExpect(status().isOk());

            verify(uploadQuotaService).reserve("10001", "test.jpg");
        }

        @Test
        @DisplayName("上传失败 - 退还已预占的额度")
        void shouldRefundQuotaWhenUploadFails() throws Exception {
            given(uploadQuotaService.reserve(anyString(), anyString())).willReturn(true);
            willAnswer(invocation -> {
                throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败");
            }).given(fileService).upload(any());

            mockMvc.perform(multipart("/upload").file(imageFile())
                            .header("X-User-Id", "10001"))
                    .andExpect(status().isBadRequest());

            verify(uploadQuotaService).refund("10001", "test.jpg");
        }

        @Test
        @DisplayName("Fail-Open（未预占额度）- 上传失败时不退还")
        void shouldNotRefundWhenNotReserved() throws Exception {
            given(uploadQuotaService.reserve(anyString(), anyString())).willReturn(false);
            willAnswer(invocation -> {
                throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败");
            }).given(fileService).upload(any());

            mockMvc.perform(multipart("/upload").file(imageFile())
                            .header("X-User-Id", "10001"))
                    .andExpect(status().isBadRequest());

            verify(uploadQuotaService, never()).refund(anyString(), anyString());
        }
    }
}
