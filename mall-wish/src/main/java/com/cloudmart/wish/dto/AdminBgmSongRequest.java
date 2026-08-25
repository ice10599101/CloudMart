package com.cloudmart.wish.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * 管理端 BGM 歌曲登记/编辑请求（Sprint 2.3 BGM 曲库）。
 *
 * <p>登记与编辑共用：url 仅登记时消费（编辑不可改——文件已上传 OSS，
 * 换歌走重新上传+登记）；title/sort 均可改。</p>
 *
 * @param title    歌曲标题（必填，1-128 字）
 * @param url      音频地址（登记必填，编辑忽略；须为 http(s) 直链）
 * @param fileSize 文件大小字节（登记可选，展示用）
 * @param sort     播放顺序（0-9999，默认 0；升序播放）
 */
@Builder
public record AdminBgmSongRequest(
        @NotBlank(message = "歌曲标题不能为空")
        @Size(max = 128, message = "歌曲标题不能超过 128 字")
        String title,

        @Size(max = 512, message = "音频地址长度非法")
        String url,

        @Min(value = 0, message = "文件大小非法")
        Long fileSize,

        @Min(value = 0, message = "播放顺序不能为负")
        @Max(value = 9999, message = "播放顺序最大 9999")
        Integer sort) {
}
