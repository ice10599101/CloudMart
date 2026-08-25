package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.AdminBgmSongRequest;
import com.cloudmart.wish.service.AdminBgmService;
import com.cloudmart.wish.vo.BgmSongVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台背景音乐曲库 Controller（Sprint 2.3：上传歌曲 + 勾选播放列表）。
 *
 * <p>路由前缀 /admin/bgm，仅内部服务调用（mall-admin 经 Feign 代理转发，
 * hasRole('INTERNAL') 由 X-Internal-Call 头授予）；权限点
 * {@code business:wishBgm:*} 在管理后台角色界面配置。管理员身份经
 * {@code X-User-Id} 头透传（AdminFeignInterceptor）。</p>
 *
 * <p>上传链路：前端先调 mall-file POST /file/upload 传 mp3（白名单已含，
 * 上限 50MB）拿到 URL，再调本接口登记；本模块不感知文件上传细节。</p>
 */
@RestController
@RequestMapping("/admin/bgm")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-背景音乐", description = "BGM 曲库管理：上传登记 + 启停勾选 + 排序 + 删除")
@RequiredArgsConstructor
public class AdminBgmController {

    private final AdminBgmService adminBgmService;

    @GetMapping
    @Operation(summary = "全量歌曲列表", description = "含未激活（管理端表格展示全部曲库），"
            + "sort 升序；试听用 url 直链播放")
    public ApiResponse<List<BgmSongVO>> listSongs() {
        return ApiResponse.ok(adminBgmService.listSongs());
    }

    @PostMapping
    @Operation(summary = "登记歌曲", description = "mall-file 上传 mp3 完成后调用；"
            + "url 须为 http(s) 直链。默认未加入播放列表（需再调启停勾选）")
    public ApiResponse<BgmSongVO> createSong(
            @Valid @RequestBody AdminBgmSongRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(adminBgmService.createSong(request, adminUserId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑歌曲", description = "title/sort 可改；url 不可改"
            + "（文件已上传 OSS，换歌走重新上传+登记）")
    public ApiResponse<BgmSongVO> updateSong(
            @Parameter(description = "歌曲 ID", required = true) @PathVariable("id") Long songId,
            @Valid @RequestBody AdminBgmSongRequest request) {
        return ApiResponse.ok(adminBgmService.updateSong(songId, request));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启停歌曲（勾选播放列表）", description = "active=true 加入播放列表；"
            + "多首激活=顺序循环，单首激活=单曲循环；空列表四端回退默认曲")
    public ApiResponse<BgmSongVO> updateSongStatus(
            @Parameter(description = "歌曲 ID", required = true) @PathVariable("id") Long songId,
            @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        return ApiResponse.ok(adminBgmService.updateSongStatus(songId,
                active != null && active));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除歌曲", description = "物理删除元数据行（OSS 音频文件保留，"
            + "误删可重新登记同 URL 恢复）；正在播放列表中则同时移出")
    public ApiResponse<Void> deleteSong(
            @Parameter(description = "歌曲 ID", required = true) @PathVariable("id") Long songId) {
        adminBgmService.deleteSong(songId);
        return ApiResponse.ok(null);
    }
}
