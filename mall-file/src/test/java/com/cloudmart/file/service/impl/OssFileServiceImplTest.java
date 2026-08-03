package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OssFileServiceImpl 单元测试")
class OssFileServiceImplTest {

    private static final String ALLOWED_EXTENSIONS = "jpg,jpeg,png,gif,bmp,webp,svg,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,mp4,mp3";
    private static final long MAX_SIZE = 52428800L;

    @Mock
    private FileStorageService fileStorageService;

    private OssFileServiceImpl ossFileService;

    private MultipartFile buildMockFile(String filename, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        when(file.getOriginalFilename()).thenReturn(filename);
        return file;
    }

    @BeforeEach
    void setUp() {
        ossFileService = new OssFileServiceImpl(fileStorageService, ALLOWED_EXTENSIONS, MAX_SIZE);
    }

    @Nested
    @DisplayName("upload 测试")
    class UploadTests {

        @Test
        @DisplayName("上传文件 - 成功返回URL")
        void shouldUploadFileSuccessfully() {
            MultipartFile file = buildMockFile("test.jpg", 1024L);
            UploadPretreatment pretreatment = mock(UploadPretreatment.class);
            FileInfo fileInfo = mock(FileInfo.class);

            when(fileStorageService.of(any(MultipartFile.class))).thenReturn(pretreatment);
            when(pretreatment.setPath(anyString())).thenReturn(pretreatment);
            when(pretreatment.upload()).thenReturn(fileInfo);
            when(fileInfo.getUrl()).thenReturn("http://oss.example.com/20260531/test.jpg");

            String url = ossFileService.upload(file);

            assertThat(url).isEqualTo("http://oss.example.com/20260531/test.jpg");
        }

        @Test
        @DisplayName("上传文件 - 文件为null时抛异常")
        void shouldThrowWhenFileIsNull() {
            assertThatThrownBy(() -> ossFileService.upload(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_EMPTY");
        }

        @Test
        @DisplayName("上传文件 - 文件为空时抛异常")
        void shouldThrowWhenFileIsEmpty() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_EMPTY");
        }

        @Test
        @DisplayName("上传文件 - 文件大小超过限制时抛异常")
        void shouldThrowWhenFileTooLarge() {
            MultipartFile file = buildMockFile("large.jpg", 100_000_000L);

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_TOO_LARGE");
        }

        @Test
        @DisplayName("上传文件 - 文件名为null时抛异常")
        void shouldThrowWhenFileNameIsNull() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(1024L);
            when(file.getOriginalFilename()).thenReturn(null);

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_NAME_INVALID");
        }

        @Test
        @DisplayName("上传文件 - 文件名为空白时抛异常")
        void shouldThrowWhenFileNameIsBlank() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(1024L);
            when(file.getOriginalFilename()).thenReturn("   ");

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_NAME_INVALID");
        }

        @Test
        @DisplayName("上传文件 - 不支持的文件类型时抛异常")
        void shouldThrowWhenFileTypeNotAllowed() {
            MultipartFile file = buildMockFile("malware.exe", 1024L);

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_TYPE_NOT_ALLOWED");
        }

        @Test
        @DisplayName("上传文件 - 无扩展名时抛异常")
        void shouldThrowWhenNoExtension() {
            MultipartFile file = buildMockFile("noextension", 1024L);

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_TYPE_NOT_ALLOWED");
        }

        @Test
        @DisplayName("上传文件 - 上传返回null时抛异常")
        void shouldThrowWhenUploadReturnsNull() {
            MultipartFile file = buildMockFile("test.jpg", 1024L);
            UploadPretreatment pretreatment = mock(UploadPretreatment.class);

            when(fileStorageService.of(any(MultipartFile.class))).thenReturn(pretreatment);
            when(pretreatment.setPath(anyString())).thenReturn(pretreatment);
            when(pretreatment.upload()).thenReturn(null);

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_UPLOAD_FAILED");
        }

        @Test
        @DisplayName("上传文件 - 存储服务抛出运行时异常时包装为BusinessException")
        void shouldWrapRuntimeExceptionAsBusinessException() {
            MultipartFile file = buildMockFile("test.jpg", 1024L);
            UploadPretreatment pretreatment = mock(UploadPretreatment.class);

            when(fileStorageService.of(any(MultipartFile.class))).thenReturn(pretreatment);
            when(pretreatment.setPath(anyString())).thenReturn(pretreatment);
            when(pretreatment.upload()).thenThrow(new RuntimeException("OSS连接超时"));

            assertThatThrownBy(() -> ossFileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_UPLOAD_FAILED");
        }
    }

    @Nested
    @DisplayName("delete 测试")
    class DeleteTests {

        @Test
        @DisplayName("删除文件 - 成功调用")
        void shouldDeleteFile() {
            ossFileService.delete("http://oss.example.com/20260531/test.jpg");

            verify(fileStorageService).delete("http://oss.example.com/20260531/test.jpg");
        }

        @Test
        @DisplayName("删除文件 - URL为null时抛异常")
        void shouldThrowWhenUrlIsNull() {
            assertThatThrownBy(() -> ossFileService.delete(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_URL_EMPTY");
        }

        @Test
        @DisplayName("删除文件 - URL为空白时抛异常")
        void shouldThrowWhenUrlIsBlank() {
            assertThatThrownBy(() -> ossFileService.delete("  "))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_URL_EMPTY");
        }
    }
}
