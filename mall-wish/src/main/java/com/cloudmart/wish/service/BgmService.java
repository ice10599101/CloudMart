package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.BgmSongVO;

import java.util.List;

/**
 * 背景音乐公开服务（Sprint 2.3 BGM 曲库）。
 *
 * <p>四端 WishBGM 播放器数据源：按 sort 升序返回勾选播放的歌曲；
 * 低频读 + 表极小（几十行），DB 直查不加缓存（管理 CRUD 天然即时生效，
 * 无缓存一致性问题；DB 异常时公开接口自然 500，前端回退默认曲）。</p>
 */
public interface BgmService {

    /**
     * 当前播放列表（is_active=1，sort 升序 + id 稳定排序）。
     *
     * @return 播放歌曲列表；空列表由前端回退默认曲
     */
    List<BgmSongVO> getPlaylist();
}
