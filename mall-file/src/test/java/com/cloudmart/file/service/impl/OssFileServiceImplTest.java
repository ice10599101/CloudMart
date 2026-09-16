package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OssFileServiceImpl（本地存储）单元测试")
class OssFileServiceImplTest {

    private static final String ALLOWED_EXTENSIONS = "jpg,jpeg,png,gif,bmp,webp,svg,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,mp4,mp3";
    private static final long MAX_SIZE = 52428800L;
    private static final Pattern URL_PATTERN = Pattern.compile("^/files/(pic|music|video|file)/\\d{8}/[0-9a-f]{32}\\.[a-z0-9]+$");

    @TempDir
    Path tempDir;

    private OssFileServiceImpl fileService;

    private MultipartFile buildMockFile(String filename, long size, byte[] content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return file;
    }

    @BeforeEach
    void setUp() {
        fileService = new OssFileServiceImpl(tempDir.toString(), ALLOWED_EXTENSIONS, MAX_SIZE);
    }

    @Nested
    @DisplayName("upload 测试")
    class UploadTests {

        @Test
        @DisplayName("上传图片 - 保存到 pic 分类并返回相对 URL")
        void shouldUploadImageToPicCategory() throws IOException {
            MultipartFile file = buildMockFile("avatar.jpg", 3L, new byte[]{1, 2, 3});

            String url = fileService.upload(file);

            assertThat(url).matches(URL_PATTERN);
            assertThat(url).startsWith("/files/pic/");
            Path stored = tempDir.resolve(url.substring("/files/".length()));
            assertThat(Files.exists(stored)).isTrue();
            assertThat(Files.readAllBytes(stored)).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("上传 mp3 - 保存到 music 分类")
        void shouldUploadMp3ToMusicCategory() throws IOException {
            MultipartFile file = buildMockFile("song.mp3", 4L, new byte[]{9, 9});

            String url = fileService.upload(file);

            assertThat(url).startsWith("/files/music/");
            assertThat(Files.exists(tempDir.resolve(url.substring("/files/".length())))).isTrue();
        }

        @Test
        @DisplayName("上传 mp4 - 保存到 video 分类")
        void shouldUploadMp4ToVideoCategory() throws IOException {
            MultipartFile file = buildMockFile("clip.mp4", 4L, new byte[]{8, 8});

            String url = fileService.upload(file);

            assertThat(url).startsWith("/files/video/");
        }

        @Test
        @DisplayName("上传 pdf - 保存到 file 分类")
        void shouldUploadPdfToFileCategory() throws IOException {
            MultipartFile file = buildMockFile("doc.pdf", 4L, new byte[]{7});

            String url = fileService.upload(file);

            assertThat(url).startsWith("/files/file/");
        }

        @Test
        @DisplayName("上传文件 - 文件为null时抛异常")
        void shouldThrowWhenFileIsNull() {
            assertThatThrownBy(() -> fileService.upload(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_EMPTY");
        }

        @Test
        @DisplayName("上传文件 - 文件为空时抛异常")
        void shouldThrowWhenFileIsEmpty() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);

            assertThatThrownBy(() -> fileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_EMPTY");
        }

        @Test
        @DisplayName("上传文件 - 文件大小超过限制时抛异常")
        void shouldThrowWhenFileTooLarge() throws IOException {
            MultipartFile file = buildMockFile("large.jpg", 100_000_000L, new byte[0]);

            assertThatThrownBy(() -> fileService.upload(file))
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

            assertThatThrownBy(() -> fileService.upload(file))
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

            assertThatThrownBy(() -> fileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_NAME_INVALID");
        }

        @Test
        @DisplayName("上传文件 - 不支持的文件类型时抛异常")
        void shouldThrowWhenFileTypeNotAllowed() throws IOException {
            MultipartFile file = buildMockFile("malware.exe", 1024L, new byte[0]);

            assertThatThrownBy(() -> fileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_TYPE_NOT_ALLOWED");
        }

        @Test
        @DisplayName("上传文件 - 无扩展名时抛异常")
        void shouldThrowWhenNoExtension() throws IOException {
            MultipartFile file = buildMockFile("noextension", 1024L, new byte[0]);

            assertThatThrownBy(() -> fileService.upload(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_TYPE_NOT_ALLOWED");
        }
    }

    @Nested
    @DisplayName("delete 测试")
    class DeleteTests {

        @Test
        @DisplayName("删除本地文件 - 相对 URL 删除成功")
        void shouldDeleteLocalFile() throws IOException {
            Path file = tempDir.resolve("pic/20260917/test.jpg");
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[]{1});

            fileService.delete("/files/pic/20260917/test.jpg");

            assertThat(Files.exists(file)).isFalse();
        }

        @Test
        @DisplayName("删除不存在文件 - 幂等成功不抛异常")
        void shouldDeleteMissingFileIdempotently() {
            fileService.delete("/files/pic/20260917/missing.jpg");
        }

        @Test
        @DisplayName("删除存量 OSS 域名 URL - 跳过不抛异常")
        void shouldSkipLegacyOssUrl() {
            fileService.delete("https://oss-ysf.oss-cn-guangzhou.aliyuncs.com/cloudmart/20260531/test.jpg");
        }

        @Test
        @DisplayName("删除路径穿越 URL - 拒绝且不删除")
        void shouldRejectPathTraversal() throws IOException {
            Path outside = tempDir.resolveSibling("evil.txt");
            Files.write(outside, new byte[]{1});

            fileService.delete("/files/../evil.txt");

            assertThat(Files.exists(outside)).isTrue();
        }

        @Test
        @DisplayName("删除文件 - URL为null时抛异常")
        void shouldThrowWhenUrlIsNull() {
            assertThatThrownBy(() -> fileService.delete(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_URL_EMPTY");
        }

        @Test
        @DisplayName("删除文件 - URL为空白时抛异常")
        void shouldThrowWhenUrlIsBlank() {
            assertThatThrownBy(() -> fileService.delete("  "))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("FILE_URL_EMPTY");
        }
    }
}