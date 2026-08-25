package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminBgmSongRequest;
import com.cloudmart.wish.entity.WishBgmSong;
import com.cloudmart.wish.repository.WishBgmSongMapper;
import com.cloudmart.wish.vo.BgmSongVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminBgmServiceImpl 单元测试（Sprint 2.3 BGM 曲库管理端契约）。
 * DB 真实读写与播放列表口径断言见 BgmIntegrationTest。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBgmServiceImpl 单元测试")
class AdminBgmServiceImplTest {

    private static final Long ADMIN_ID = 88L;

    @Mock
    private WishBgmSongMapper bgmSongMapper;

    private AdminBgmServiceImpl adminBgmService;

    @BeforeAll
    static void initEntityMetadata() {
        // 纯单测环境无 MyBatis-Plus 启动流程，手动初始化 LambdaUpdateWrapper
        // 所需的实体列缓存（项目既有模式，见 TreeEnvServiceImplTest）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WishBgmSong.class);
    }

    @BeforeEach
    void setUp() {
        adminBgmService = new AdminBgmServiceImpl(bgmSongMapper);
    }

    @Nested
    @DisplayName("createSong - 登记歌曲")
    class CreateSongTests {

        @Test
        @DisplayName("登记成功：默认未激活 + fileSize/sort 缺省 0 + 记录上传管理员")
        void createSong_defaultsInactiveAndZero() {
            adminBgmService.createSong(AdminBgmSongRequest.builder()
                    .title("星语心愿").url("https://oss.example.com/bgm/a.mp3")
                    .build(), ADMIN_ID);

            ArgumentCaptor<WishBgmSong> captor = ArgumentCaptor.forClass(WishBgmSong.class);
            verify(bgmSongMapper).insert(captor.capture());
            WishBgmSong inserted = captor.getValue();
            assertThat(inserted.getTitle()).isEqualTo("星语心愿");
            assertThat(inserted.getUrl()).isEqualTo("https://oss.example.com/bgm/a.mp3");
            assertThat(inserted.getIsActive()).isFalse();
            assertThat(inserted.getFileSize()).isZero();
            assertThat(inserted.getSort()).isZero();
            assertThat(inserted.getUploadedBy()).isEqualTo(ADMIN_ID);
        }

        @Test
        @DisplayName("非法 url 拒绝：非 http(s) 直链报 BGM_SONG_URL_INVALID")
        void createSong_nonHttpUrlRejected() {
            assertThatThrownBy(() -> adminBgmService.createSong(AdminBgmSongRequest.builder()
                    .title("本地文件").url("ftp://example.com/a.mp3").build(), ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_URL_INVALID);
            verify(bgmSongMapper, never()).insert(any(WishBgmSong.class));
        }

        @Test
        @DisplayName("空 url 拒绝：缺 url 同报 BGM_SONG_URL_INVALID")
        void createSong_blankUrlRejected() {
            assertThatThrownBy(() -> adminBgmService.createSong(AdminBgmSongRequest.builder()
                    .title("无地址").url("  ").build(), ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_URL_INVALID);
        }
    }

    @Nested
    @DisplayName("updateSong - 编辑歌曲")
    class UpdateSongTests {

        @Test
        @DisplayName("编辑成功：title/sort 更新，url 保持原值不可改")
        void updateSong_keepsUrl() {
            WishBgmSong song = song(1L, "旧标题", "https://oss.example.com/old.mp3", 0, true);
            when(bgmSongMapper.selectById(1L)).thenReturn(song);

            BgmSongVO vo = adminBgmService.updateSong(1L, AdminBgmSongRequest.builder()
                    .title("新标题").url("https://evil.example.com/new.mp3").sort(5).build());

            assertThat(vo.title()).isEqualTo("新标题");
            assertThat(vo.sort()).isEqualTo(5);
            // url 忽略请求值，保持原 OSS 地址（防悬空引用）
            assertThat(vo.url()).isEqualTo("https://oss.example.com/old.mp3");
        }

        @Test
        @DisplayName("歌曲不存在报 BGM_SONG_NOT_FOUND")
        void updateSong_notFound() {
            when(bgmSongMapper.selectById(404L)).thenReturn(null);
            assertThatThrownBy(() -> adminBgmService.updateSong(404L,
                    AdminBgmSongRequest.builder().title("x").build()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateSongStatus - 启停勾选")
    class UpdateSongStatusTests {

        @Test
        @DisplayName("启停成功：set is_active 目标值并返回最新状态")
        void updateSongStatus_setsActive() {
            WishBgmSong song = song(1L, "星语心愿", "https://oss.example.com/a.mp3", 0, false);
            when(bgmSongMapper.selectById(1L)).thenReturn(song);

            BgmSongVO vo = adminBgmService.updateSongStatus(1L, true);

            assertThat(vo.active()).isTrue();
            verify(bgmSongMapper).update(any(), any());
        }
    }

    @Nested
    @DisplayName("deleteSong - 删除歌曲")
    class DeleteSongTests {

        @Test
        @DisplayName("删除成功：物理删除元数据行")
        void deleteSong_deletes() {
            when(bgmSongMapper.selectById(1L)).thenReturn(song(1L, "x", "https://o/a.mp3", 0, true));

            adminBgmService.deleteSong(1L);

            verify(bgmSongMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除不存在歌曲报 BGM_SONG_NOT_FOUND")
        void deleteSong_notFound() {
            when(bgmSongMapper.selectById(404L)).thenReturn(null);
            assertThatThrownBy(() -> adminBgmService.deleteSong(404L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("listSongs - 全量列表")
    class ListSongsTests {

        @Test
        @DisplayName("含未激活歌曲：管理端表格需展示全部曲库")
        void listSongs_includesInactive() {
            when(bgmSongMapper.selectList(any())).thenReturn(List.of(
                    song(1L, "激活曲", "https://o/a.mp3", 0, true),
                    song(2L, "未激活曲", "https://o/b.mp3", 1, false)));

            List<BgmSongVO> result = adminBgmService.listSongs();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).active()).isTrue();
            assertThat(result.get(1).active()).isFalse();
        }
    }

    private static WishBgmSong song(Long id, String title, String url, Integer sort, boolean active) {
        WishBgmSong song = new WishBgmSong();
        song.setId(id);
        song.setTitle(title);
        song.setUrl(url);
        song.setSort(sort);
        song.setIsActive(active);
        song.setUploadedBy(ADMIN_ID);
        return song;
    }
}
