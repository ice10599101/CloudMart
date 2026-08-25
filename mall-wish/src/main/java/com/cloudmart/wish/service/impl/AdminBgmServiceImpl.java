package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminBgmSongRequest;
import com.cloudmart.wish.entity.WishBgmSong;
import com.cloudmart.wish.repository.WishBgmSongMapper;
import com.cloudmart.wish.service.AdminBgmService;
import com.cloudmart.wish.vo.BgmSongVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端背景音乐曲库服务实现（Sprint 2.3：上传歌曲 + 勾选播放列表）。
 *
 * <p>上传链路：管理后台先调 mall-file POST /file/upload（白名单含 mp3）
 * 拿到 OSS URL，再调本服务登记元数据——mall-file 不感知曲库业务。
 * 删除为物理删除（音频元数据非核心业务数据；OSS 文件保留，误删可重新
 * 登记同 URL 恢复）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBgmServiceImpl implements AdminBgmService {

    private final WishBgmSongMapper bgmSongMapper;

    @Override
    public List<BgmSongVO> listSongs() {
        return bgmSongMapper.selectList(new LambdaQueryWrapper<WishBgmSong>()
                        .orderByAsc(WishBgmSong::getSort)
                        .orderByAsc(WishBgmSong::getId))
                .stream()
                .map(AdminBgmServiceImpl::toAdminVO)
                .toList();
    }

    @Override
    @Transactional
    public BgmSongVO createSong(AdminBgmSongRequest request, Long adminUserId) {
        String url = request.url() == null ? "" : request.url().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(WishErrorCodes.BGM_SONG_URL_INVALID,
                    "音频地址须为 http(s) 直链: " + url);
        }

        WishBgmSong song = new WishBgmSong();
        song.setTitle(request.title().trim());
        song.setUrl(url);
        song.setFileSize(request.fileSize() != null ? request.fileSize() : 0L);
        song.setSort(request.sort() != null ? request.sort() : 0);
        song.setIsActive(false);
        song.setUploadedBy(adminUserId);
        bgmSongMapper.insert(song);

        log.info("管理员登记 BGM 歌曲: id={}, title={}, admin={}",
                song.getId(), song.getTitle(), adminUserId);
        return toAdminVO(song);
    }

    @Override
    @Transactional
    public BgmSongVO updateSong(Long songId, AdminBgmSongRequest request) {
        WishBgmSong song = requireSong(songId);
        song.setTitle(request.title().trim());
        if (request.sort() != null) {
            song.setSort(request.sort());
        }
        // url 不可改：文件已上传 OSS，换歌走重新上传+登记（避免悬空引用）
        bgmSongMapper.updateById(song);
        return toAdminVO(song);
    }

    @Override
    @Transactional
    public BgmSongVO updateSongStatus(Long songId, boolean active) {
        WishBgmSong song = requireSong(songId);
        bgmSongMapper.update(null, new LambdaUpdateWrapper<WishBgmSong>()
                .eq(WishBgmSong::getId, songId)
                .set(WishBgmSong::getIsActive, active));
        song.setIsActive(active);
        return toAdminVO(song);
    }

    @Override
    @Transactional
    public void deleteSong(Long songId) {
        requireSong(songId);
        bgmSongMapper.deleteById(songId);
        log.info("管理员删除 BGM 歌曲: id={}", songId);
    }

    private WishBgmSong requireSong(Long songId) {
        WishBgmSong song = bgmSongMapper.selectById(songId);
        if (song == null) {
            throw new BusinessException(WishErrorCodes.BGM_SONG_NOT_FOUND,
                    "BGM 歌曲不存在: " + songId);
        }
        return song;
    }

    private static BgmSongVO toAdminVO(WishBgmSong song) {
        return BgmSongVO.builder()
                .id(song.getId())
                .title(song.getTitle())
                .url(song.getUrl())
                .fileSize(song.getFileSize())
                .sort(song.getSort())
                .active(Boolean.TRUE.equals(song.getIsActive()))
                .createdAt(song.getCreatedAt())
                .build();
    }
}
