package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminBgmSongRequest;
import com.cloudmart.wish.service.AdminBgmService;
import com.cloudmart.wish.service.BgmService;
import com.cloudmart.wish.vo.BgmSongVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BGM 曲库集成测试（Sprint 2.3，真实 mysql-it）。
 *
 * <p>覆盖：管理端登记（url 直链校验/默认值/审计字段）→ 编辑（url 不可改）
 * → 启停（播放列表勾选语义）→ 删除（物理删除）→ 公开播放列表
 * （is_active 过滤 + sort 升序 + 管理端字段不出公开 VO）。</p>
 */
@DisplayName("BGM 曲库集成测试")
class BgmIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private AdminBgmService adminBgmService;

    @Autowired
    private BgmService bgmService;

    private static final long ADMIN_USER_ID = 99001L;

    /** 种子：直插一首歌（绕过服务层，模拟历史登记数据） */
    private void seedSong(String title, String url, int sort, boolean active) {
        jdbcTemplate.update(
                "INSERT INTO wish_bgm_song (id, title, url, file_size, sort, is_active, uploaded_by, "
                        + "created_at, updated_at) VALUES (?, ?, ?, 1024, ?, ?, ?, NOW(), NOW())",
                System.nanoTime(), title, url, sort, active, ADMIN_USER_ID);
    }

    private AdminBgmSongRequest request(String title, String url, Integer sort) {
        return AdminBgmSongRequest.builder()
                .title(title).url(url).sort(sort).fileSize(2048L)
                .build();
    }

    @Nested
    @DisplayName("管理端登记")
    class CreateTests {

        @Test
        @DisplayName("正常登记：默认未激活、默认值补齐、审计字段落库")
        void createSong_defaultsAndAudit() {
            BgmSongVO vo = adminBgmService.createSong(
                    request("星海", "https://oss.example.com/bgm/star-sea.mp3", null), ADMIN_USER_ID);

            assertThat(vo.id()).isNotNull();
            assertThat(vo.title()).isEqualTo("星海");
            assertThat(vo.url()).isEqualTo("https://oss.example.com/bgm/star-sea.mp3");
            assertThat(vo.fileSize()).isEqualTo(2048L);
            assertThat(vo.sort()).isZero();
            assertThat(vo.active()).isFalse();

            // 新歌未激活：不进公开播放列表
            assertThat(bgmService.getPlaylist()).isEmpty();

            var row = jdbcTemplate.queryForMap(
                    "SELECT file_size, is_active, uploaded_by FROM wish_bgm_song WHERE id = ?",
                    vo.id());

            // 修复 ClassCastException：将数据库返回的数值统一转型为 Number 再获取 longValue
            assertThat(((Number) row.get("file_size")).longValue()).isEqualTo(2048L);
            assertThat((Boolean) row.get("is_active")).isFalse();
            assertThat(((Number) row.get("uploaded_by")).longValue()).isEqualTo(ADMIN_USER_ID);
        }

        @Test
        @DisplayName("非法 url：非 http(s) 直链拒绝（BGM_SONG_URL_INVALID）")
        void createSong_invalidUrl() {
            assertThatThrownBy(() -> adminBgmService.createSong(
                    request("坏链", "ftp://oss.example.com/bgm/a.mp3", 0), ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_URL_INVALID);

            assertThatThrownBy(() -> adminBgmService.createSong(
                    AdminBgmSongRequest.builder().title("空链").build(), ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_URL_INVALID);
        }
    }

    @Nested
    @DisplayName("编辑与启停")
    class UpdateTests {

        @Test
        @DisplayName("编辑：title/sort 可改，url 保持不变（换歌走重新上传+登记）")
        void updateSong_titleSortOnly() {
            BgmSongVO created = adminBgmService.createSong(
                    request("旧标题", "https://oss.example.com/bgm/old.mp3", 1), ADMIN_USER_ID);

            BgmSongVO updated = adminBgmService.updateSong(created.id(),
                    request("新标题", "https://oss.example.com/bgm/hack.mp3", 99));

            assertThat(updated.title()).isEqualTo("新标题");
            assertThat(updated.sort()).isEqualTo(99);
            assertThat(updated.url()).isEqualTo("https://oss.example.com/bgm/old.mp3");
        }

        @Test
        @DisplayName("启停语义：激活进播放列表，下架即消失（CRUD 即时生效无缓存）")
        void updateSongStatus_playlistMembership() {
            BgmSongVO created = adminBgmService.createSong(
                    request("森林物语", "https://oss.example.com/bgm/forest.mp3", 0), ADMIN_USER_ID);

            BgmSongVO activated = adminBgmService.updateSongStatus(created.id(), true);
            assertThat(activated.active()).isTrue();
            assertThat(bgmService.getPlaylist()).hasSize(1);

            BgmSongVO deactivated = adminBgmService.updateSongStatus(created.id(), false);
            assertThat(deactivated.active()).isFalse();
            assertThat(bgmService.getPlaylist()).isEmpty();
        }

        @Test
        @DisplayName("歌曲不存在：编辑/启停/删除均拒绝（BGM_SONG_NOT_FOUND）")
        void missingSong_notFound() {
            AdminBgmSongRequest req = request("幽灵", null, null);
            assertThatThrownBy(() -> adminBgmService.updateSong(-1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_NOT_FOUND);
            assertThatThrownBy(() -> adminBgmService.updateSongStatus(-1L, true))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> adminBgmService.deleteSong(-1L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("公开播放列表")
    class PlaylistTests {

        @Test
        @DisplayName("激活过滤 + sort 升序 + id 稳定排序；管理端字段不出公开 VO")
        void playlist_filteredAndSorted() {
            seedSong("B 曲", "https://oss.example.com/bgm/b.mp3", 20, true);
            seedSong("A 曲", "https://oss.example.com/bgm/a.mp3", 10, true);
            seedSong("未激活曲", "https://oss.example.com/bgm/off.mp3", 1, false);

            List<BgmSongVO> playlist = bgmService.getPlaylist();

            assertThat(playlist).hasSize(2);
            assertThat(playlist).extracting(BgmSongVO::title)
                    .containsExactly("A 曲", "B 曲");
            // 公开 VO 不泄露管理端字段（fileSize/createdAt 为 null，active 恒 true）
            assertThat(playlist).allSatisfy(vo -> {
                assertThat(vo.fileSize()).isNull();
                assertThat(vo.createdAt()).isNull();
                assertThat(vo.active()).isTrue();
                assertThat(vo.url()).startsWith("https://");
            });
        }

        @Test
        @DisplayName("sort 相同按 id 升序稳定排序（登记先后即播放先后）")
        void playlist_sameSortStableById() {
            BgmSongVO first = adminBgmService.createSong(
                    request("先登记", "https://oss.example.com/bgm/1.mp3", 5), ADMIN_USER_ID);
            BgmSongVO second = adminBgmService.createSong(
                    request("后登记", "https://oss.example.com/bgm/2.mp3", 5), ADMIN_USER_ID);
            adminBgmService.updateSongStatus(first.id(), true);
            adminBgmService.updateSongStatus(second.id(), true);

            List<BgmSongVO> playlist = bgmService.getPlaylist();

            assertThat(playlist).extracting(BgmSongVO::title)
                    .containsExactly("先登记", "后登记");
        }

        @Test
        @DisplayName("空曲库：返回空列表（前端回退默认曲）")
        void emptyLibrary_returnsEmpty() {
            assertThat(bgmService.getPlaylist()).isEmpty();
        }
    }

    @Nested
    @DisplayName("删除")
    class DeleteTests {

        @Test
        @DisplayName("删除：物理移除元数据行，再操作报不存在")
        void deleteSong_removesRow() {
            BgmSongVO created = adminBgmService.createSong(
                    request("一次性", "https://oss.example.com/bgm/once.mp3", 0), ADMIN_USER_ID);
            adminBgmService.updateSongStatus(created.id(), true);
            assertThat(bgmService.getPlaylist()).hasSize(1);

            adminBgmService.deleteSong(created.id());

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM wish_bgm_song WHERE id = ?", Integer.class, created.id());
            assertThat(count).isZero();
            assertThat(bgmService.getPlaylist()).isEmpty();
            assertThatThrownBy(() -> adminBgmService.deleteSong(created.id()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(WishErrorCodes.BGM_SONG_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("端到端：登记 → 激活 → 播放列表可见 → 下架")
    class EndToEndTests {

        @Test
        @DisplayName("管理员上传换歌全链路即时生效")
        void fullLifecycle() {
            // 1. 登记（未激活，不可见）
            BgmSongVO song = adminBgmService.createSong(
                    request("初版", "https://oss.example.com/bgm/v1.mp3", 0), ADMIN_USER_ID);
            assertThat(bgmService.getPlaylist()).isEmpty();

            // 2. 激活（可见）
            adminBgmService.updateSongStatus(song.id(), true);
            List<BgmSongVO> visible = bgmService.getPlaylist();
            assertThat(visible).hasSize(1);
            assertThat(visible.get(0).title()).isEqualTo("初版");

            // 3. 编辑标题（播放列表曲名即时更新）
            adminBgmService.updateSong(song.id(), request("终版", null, 0));
            assertThat(bgmService.getPlaylist().get(0).title()).isEqualTo("终版");

            // 4. 下架（不可见）
            adminBgmService.updateSongStatus(song.id(), false);
            assertThat(bgmService.getPlaylist()).isEmpty();
        }
    }
}