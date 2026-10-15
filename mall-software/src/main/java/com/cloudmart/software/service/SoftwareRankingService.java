package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.vo.software.SoftwareCardVO;

/**
 * 软件排行榜（方案 4.5，P1）：7 天获取链接去重人数榜 / 7 天收藏榜 / 评分榜。
 * 榜单口径展示在页面（含评分人数与窗口），禁止运营手填"真实下载量"。
 */
public interface SoftwareRankingService {

    /**
     * @param type access/favorite/rating（非法值回落 access）
     * @return 可见软件榜单（缓存命中后仍按实时可见性过滤，A26）
     */
    Page<SoftwareCardVO> rankings(String type, int page, int size);
}
