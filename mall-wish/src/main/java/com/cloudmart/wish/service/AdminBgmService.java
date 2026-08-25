package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.AdminBgmSongRequest;
import com.cloudmart.wish.vo.BgmSongVO;

import java.util.List;

/**
 * 管理端背景音乐曲库服务（Sprint 2.3：上传歌曲 + 勾选播放列表）。
 */
public interface AdminBgmService {

    /** 全量歌曲列表（含未激活，sort 升序 + id 稳定排序；管理端表格） */
    List<BgmSongVO> listSongs();

    /**
     * 登记歌曲（mall-file 上传完成后调用）。
     *
     * @param request    标题/URL/大小/顺序（url 必填 http(s) 直链）
     * @param adminUserId 上传管理员（审计）
     */
    BgmSongVO createSong(AdminBgmSongRequest request, Long adminUserId);

    /** 编辑歌曲（title/sort 可改；url 不可改——换歌走重新上传+登记） */
    BgmSongVO updateSong(Long songId, AdminBgmSongRequest request);

    /**
     * 启停歌曲（勾选/取消播放列表）。
     *
     * @param active true=加入播放列表（多首激活顺序循环）
     */
    BgmSongVO updateSongStatus(Long songId, boolean active);

    /** 删除歌曲（物理删除元数据行；OSS 文件保留） */
    void deleteSong(Long songId);
}
