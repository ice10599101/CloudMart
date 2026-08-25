package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.wish.entity.WishBgmSong;
import com.cloudmart.wish.repository.WishBgmSongMapper;
import com.cloudmart.wish.service.BgmService;
import com.cloudmart.wish.vo.BgmSongVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 背景音乐公开服务实现（Sprint 2.3 BGM 曲库）。
 */
@Service
@RequiredArgsConstructor
public class BgmServiceImpl implements BgmService {

    private final WishBgmSongMapper bgmSongMapper;

    @Override
    public List<BgmSongVO> getPlaylist() {
        return bgmSongMapper.selectList(new LambdaQueryWrapper<WishBgmSong>()
                        .eq(WishBgmSong::getIsActive, true)
                        .orderByAsc(WishBgmSong::getSort)
                        .orderByAsc(WishBgmSong::getId))
                .stream()
                .map(song -> BgmSongVO.publicItem(
                        song.getId(), song.getTitle(), song.getUrl(), song.getSort()))
                .toList();
    }
}
