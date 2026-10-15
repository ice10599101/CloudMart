package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareFavorite;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareFavoriteMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.service.SoftwareFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏实现（方案 4.5/6.8）：唯一 (user_id, software_id) 幂等；
 * 新增必须公开可见谓词通过；取消只检查登录与关系归属，即使软件已下架也幂等成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareFavoriteServiceImpl implements SoftwareFavoriteService {

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareMapper softwareMapper;
    private final SoftwareFavoriteMapper favoriteMapper;

    @Override
    @Transactional
    public void favorite(Long userId, Long softwareId) {
        // 新增收藏必须满足公开可见谓词（4.5）
        if (accessPolicy.visibleSoftware(softwareId) == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        SoftwareFavorite favorite = new SoftwareFavorite();
        favorite.setSoftwareId(softwareId);
        favorite.setUserId(userId);
        try {
            favoriteMapper.insert(favorite);
            softwareMapper.incrementFavoriteCount(softwareId, 1);
        } catch (DuplicateKeyException e) {
            // 幂等：重复收藏无副作用
        }
    }

    @Override
    @Transactional
    public void unfavorite(Long userId, Long softwareId) {
        // 取消是清理本人关系：即使软件已下架也幂等成功，不返回被隐藏软件信息（4.5）
        SoftwareFavorite favorite = favoriteMapper.selectOne(
                new LambdaQueryWrapper<SoftwareFavorite>()
                        .eq(SoftwareFavorite::getUserId, userId)
                        .eq(SoftwareFavorite::getSoftwareId, softwareId));
        if (favorite == null) {
            return;
        }
        favoriteMapper.deleteById(favorite.getId());
        // 软件可能已不可见：计数仅对可见软件回减（不可见软件计数已随处置重建）
        Software software = softwareMapper.selectById(softwareId);
        if (software != null && software.getLifecycle() != SoftwareLifecycle.DELETED) {
            softwareMapper.incrementFavoriteCount(softwareId, -1);
        }
    }
}
