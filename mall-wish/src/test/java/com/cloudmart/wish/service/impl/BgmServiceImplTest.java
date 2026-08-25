package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.WishBgmSong;
import com.cloudmart.wish.repository.WishBgmSongMapper;
import com.cloudmart.wish.vo.BgmSongVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BgmServiceImpl 单元测试（Sprint 2.3 公开播放列表契约）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BgmServiceImpl 单元测试")
class BgmServiceImplTest {

    @Mock
    private WishBgmSongMapper bgmSongMapper;

    private BgmServiceImpl bgmService;

    @BeforeEach
    void setUp() {
        bgmService = new BgmServiceImpl(bgmSongMapper);
    }

    @Test
    @DisplayName("播放列表按 DB 过滤结果映射且不含管理端字段（fileSize/createdAt 不外泄）")
    void playlist_onlyActiveAndLeanFields() {
        // mock 返回 DB 按 is_active=1 + sort 升序过滤后的结果
        // （真实过滤口径断言见 BgmIntegrationTest）
        when(bgmSongMapper.selectList(any())).thenReturn(List.of(
                song(1L, "星语心愿", "https://o/a.mp3", 0, true),
                song(3L, "月光森林", "https://o/c.mp3", 2, true)));

        List<BgmSongVO> playlist = bgmService.getPlaylist();

        assertThat(playlist).extracting(BgmSongVO::title)
                .containsExactly("星语心愿", "月光森林");
        assertThat(playlist).allSatisfy(song -> {
            assertThat(song.active()).isTrue();
            assertThat(song.fileSize()).isNull();
            assertThat(song.createdAt()).isNull();
        });
    }

    @Test
    @DisplayName("空曲库返回空列表（前端回退默认曲，非错误）")
    void playlist_empty() {
        when(bgmSongMapper.selectList(any())).thenReturn(List.of());

        assertThat(bgmService.getPlaylist()).isEmpty();
    }

    private static WishBgmSong song(Long id, String title, String url, Integer sort, boolean active) {
        WishBgmSong song = new WishBgmSong();
        song.setId(id);
        song.setTitle(title);
        song.setUrl(url);
        song.setSort(sort);
        song.setIsActive(active);
        return song;
    }
}
