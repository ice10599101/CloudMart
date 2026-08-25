package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 背景音乐歌曲实体（Sprint 2.3 BGM 曲库，V12 迁移）。
 *
 * <p>管理端上传 mp3（mall-file）后登记一行；{@code is_active}=1 的歌曲
 * 按sort 升序组成四端播放列表（多首顺序循环）。物理删除——音频元数据
 * 非核心业务数据，OSS 文件本身保留。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_bgm_song")
public class WishBgmSong {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 歌曲标题（管理端展示与四端播放器曲名） */
    private String title;

    /** 音频地址（mall-file 上传 OSS 后的 URL） */
    private String url;

    /** 文件大小（字节，展示用） */
    private Long fileSize;

    /** 播放顺序（升序；同序按 id 稳定排序） */
    private Integer sort;

    /** 是否在播放列表（多首激活=顺序循环播放） */
    private Boolean isActive;

    /** 上传管理员用户 ID（审计） */
    private Long uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
