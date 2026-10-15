package com.cloudmart.software.service;

/**
 * 收藏（方案 4.5/6.8）：唯一关系幂等；新增须公开可见；取消即使软件下架也幂等成功。
 */
public interface SoftwareFavoriteService {

    /** 收藏（幂等）：公开可见前提；重复收藏无副作用 */
    void favorite(Long userId, Long softwareId);

    /** 取消收藏（幂等）：只检查登录与关系归属，不返回被隐藏软件信息 */
    void unfavorite(Long userId, Long softwareId);
}
