package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.WishBgmSong;
import org.apache.ibatis.annotations.Mapper;

/**
 * 背景音乐歌曲 Mapper（Sprint 2.3 BGM 曲库）。
 */
@Mapper
public interface WishBgmSongMapper extends BaseMapper<WishBgmSong> {
}
