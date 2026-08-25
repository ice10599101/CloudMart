package com.cloudmart.wish.vo;

import lombok.Builder;

/**
 * BGM 歌曲视图对象（Sprint 2.3 BGM 曲库）。
 *
 * <p>公开播放列表与管理端列表共用：公开接口（GET /bgm/playlist）
 * 仅填充 id/title/url/sort；管理端额外填充 fileSize/active/createdAt。
 * 四端播放器按返回顺序（sort 升序）顺序循环播放。</p>
 *
 * @param id       歌曲 ID
 * @param title    歌曲标题（播放器曲名展示）
 * @param url      音频地址（OSS 直链）
 * @param fileSize 文件大小字节（仅管理端；公开列表为 null）
 * @param sort     播放顺序（升序）
 * @param active   是否在播放列表（仅管理端；公开列表恒 true）
 * @param createdAt 上传时间（仅管理端）
 */
@Builder
public record BgmSongVO(
        Long id,
        String title,
        String url,
        Long fileSize,
        Integer sort,
        Boolean active,
        java.time.LocalDateTime createdAt) {

    /** 公开播放列表项（不含管理端字段） */
    public static BgmSongVO publicItem(Long id, String title, String url, Integer sort) {
        return BgmSongVO.builder()
                .id(id).title(title).url(url).sort(sort)
                .active(true)
                .build();
    }
}
