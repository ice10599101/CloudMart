package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateBadgeRequest;
import com.cloudmart.community.dto.UpdateBadgeRequest;
import com.cloudmart.community.vo.BadgeVO;

import java.util.List;

public interface BadgeService {

    BadgeVO createBadge(CreateBadgeRequest request);

    BadgeVO updateBadge(Long badgeId, UpdateBadgeRequest request);

    void deleteBadge(Long badgeId);

    Page<BadgeVO> listBadges(int page, int size);

    void grantBadge(Long userId, Long badgeId);

    void revokeBadge(Long userId, Long badgeId);

    List<BadgeVO> getUserBadges(Long userId);

    void updateBadgeStatus(Long badgeId, Integer status);
}
