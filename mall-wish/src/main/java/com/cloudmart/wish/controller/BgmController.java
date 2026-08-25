package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.BgmService;
import com.cloudmart.wish.vo.BgmSongVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿宇宙背景音乐 Controller（Sprint 2.3 BGM 曲库）。
 *
 * <p>公开接口：播放列表非用户敏感数据，未登录页面亦需播放；歌曲管理
 * 见 AdminBgmController（仅内部调用，mall-admin 经 Feign 代理）。
 * 空列表时四端播放器回退前端内置默认曲（不视为错误）。</p>
 */
@RestController
@RequestMapping("/bgm")
@Tag(name = "背景音乐", description = "心愿宇宙四端播放器播放列表（管理端上传歌曲+勾选）")
@RequiredArgsConstructor
public class BgmController {

    private final BgmService bgmService;

    @GetMapping("/playlist")
    @Operation(summary = "当前播放列表", description = "返回勾选播放的歌曲（sort 升序），"
            + "四端播放器按顺序循环播放；多首激活即多曲列表，单首激活即单曲循环。"
            + "空列表由前端回退默认曲")
    @SentinelResource("WISH_BGM_PLAYLIST")
    public ApiResponse<List<BgmSongVO>> getPlaylist() {
        return ApiResponse.ok(bgmService.getPlaylist());
    }
}
