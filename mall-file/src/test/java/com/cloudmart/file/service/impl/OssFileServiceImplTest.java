package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S01 存储域隔离单元测试：PUBLIC → {root}/public（/files/** 可达），
 * PRIVATE → {root}/private（静态映射之外）；校验先行，失败不落盘；
 * 删除按 storageKey 并做路径穿越防护。
 */
@DisplayName("OssFileServiceImpl（S01 分域存储）单元测试")
class OssFileServiceImplTest {

    private static final String ALLOWED_EXTENSIONS = "jpg,jpeg,png,gif,bmp,webp,svg,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,mp4,mp3";
    private static final long MAX_SIZE = 52428800L;
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "^(public|private)/(pic|music|video|file)/\\d{8}/[0-9a-f]{32}\\.[a-z0-9]+$");

    @TempDir
    Path tempDir;

    private OssFileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileService = new OssFileServiceImpl(tempDir.toString(), ALLOWED_EXTENSIONS, MAX_SIZE);
    }

    @Nested
    @DisplayName("store 分域存储")
    class StoreTests {

        @Test
        @DisplayName("PUBLIC - 落 {root}/public 域，publicUrlOf 返回 /files 相对 URL")
        void store_public_landsInPublicDomain() throws IOException {
            String key = fileService.store(new byte[]{1, 2, 3}, "avatar.jpg", "PUBLIC");

            assertThat(key).matches(KEY_PATTERN).startsWith("public/pic/");
            Path stored = tempDir.resolve(key);
            assertThat(Files.exists(stored)).isTrue();
            assertThat(Files.readAllBytes(stored)).containsExactly(1, 2, 3);
            assertThat(fileService.publicUrlOf(key))
                    .isEqualTo("/files/" + key.substring("public/".length()));
        }

        @Test
        @DisplayName("PRIVATE - 落 {root}/private 域，publicUrlOf 返回 null（无公开 URL）")
        void store_private_landsInPrivateDomain() throws IOException {
            String key = fileService.store(new byte[]{4, 5}, "evidence.pdf", "PRIVATE");

            assertThat(key).startsWith("private/");
            assertThat(Files.exists(tempDir.resolve(key))).isTrue();
            assertThat(fileService.publicUrlOf(key)).isNull();
            // 私有文件不在 public 域 → 静态映射（{root}/public）不可达
            assertThat(Files.exists(tempDir.resolve("public").resolve(key.substring("private/".length())))).isFalse();
        }

        @Test
        @DisplayName("分类保持既有映射（pic/music/video/file），公开 URL 路径稳定")
        void store_categoriesStable() {
            assertThat(fileService.store(new byte[]{1}, "song.mp3", "PUBLIC")).startsWith("public/music/");
            assertThat(fileService.store(new byte[]{1}, "clip.mp4", "PUBLIC")).startsWith("public/video/");
            assertThat(fileService.store(new byte[]{1}, "doc.pdf", "PUBLIC")).startsWith("public/file/");
        }

        @Test
        @DisplayName("空内容/超限/非法扩展名 → 声明校验拒绝，磁盘无任何残留")
        void store_declarationRejected_noResidue() {
            assertThatThrownBy(() -> fileService.store(new byte[0], "a.jpg", "PUBLIC"))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FILE_EMPTY");
            assertThatThrownBy(() -> fileService.store(new byte[100_000_001], "big.jpg", "PUBLIC"))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FILE_TOO_LARGE");
            assertThatThrownBy(() -> fileService.store(new byte[]{1}, "evil.exe", "PUBLIC"))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FILE_TYPE_NOT_ALLOWED");

            // 无任何文件写出（校验先于落盘，S01 无孤儿实物）
            try (var paths = Files.walk(tempDir)) {
                assertThat(paths.filter(Files::isRegularFile).count()).isZero();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Nested
    @DisplayName("deleteByStorageKey")
    class DeleteTests {

        @Test
        @DisplayName("按存储键删除实物")
        void deleteByStorageKey_removesFile() throws IOException {
            String key = fileService.store(new byte[]{7, 8}, "a.png", "PUBLIC");
            assertThat(Files.exists(tempDir.resolve(key))).isTrue();

            fileService.deleteByStorageKey(key);

            assertThat(Files.exists(tempDir.resolve(key))).isFalse();
        }

        @Test
        @DisplayName("路径穿越防护：越出存储根的键拒绝")
        void deleteByStorageKey_pathTraversalRejected() {
            assertThatThrownBy(() -> fileService.deleteByStorageKey("../../etc/passwd"))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FILE_NAME_INVALID");
        }

        @Test
        @DisplayName("空键拒绝（FILE_URL_EMPTY）")
        void deleteByStorageKey_blankRejected() {
            assertThatThrownBy(() -> fileService.deleteByStorageKey(" "))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FILE_URL_EMPTY");
        }
    }
}
