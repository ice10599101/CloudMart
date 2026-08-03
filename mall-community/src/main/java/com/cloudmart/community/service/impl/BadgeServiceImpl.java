package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateBadgeRequest;
import com.cloudmart.community.dto.UpdateBadgeRequest;
import com.cloudmart.community.entity.Badge;
import com.cloudmart.community.entity.UserBadge;
import com.cloudmart.community.repository.BadgeMapper;
import com.cloudmart.community.repository.UserBadgeMapper;
import com.cloudmart.community.service.BadgeService;
import com.cloudmart.community.vo.BadgeVO;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class BadgeServiceImpl implements BadgeService {

    private final BadgeMapper badgeMapper;
    private final UserBadgeMapper userBadgeMapper;

    public BadgeServiceImpl(BadgeMapper badgeMapper, UserBadgeMapper userBadgeMapper) {
        this.badgeMapper = badgeMapper;
        this.userBadgeMapper = userBadgeMapper;
    }

    @Override
    @Transactional
    public BadgeVO createBadge(CreateBadgeRequest request) {
        Badge badge = new Badge();
        badge.setName(request.name());
        badge.setIcon(request.icon());
        badge.setDescription(request.description());
        badge.setCondition(request.condition());
        badge.setLevel(request.level());
        badge.setStatus(1);
        badgeMapper.insert(badge);

        return convertToVO(badge);
    }

    @Override
    @Transactional
    public BadgeVO updateBadge(Long badgeId, UpdateBadgeRequest request) {
        Badge badge = badgeMapper.selectById(badgeId);
        if (badge == null) {
            throw new BusinessException("BADGE_NOT_FOUND", "徽章不存在");
        }

        if (request.name() != null) {
            badge.setName(request.name());
        }
        if (request.icon() != null) {
            badge.setIcon(request.icon());
        }
        if (request.description() != null) {
            badge.setDescription(request.description());
        }
        if (request.condition() != null) {
            badge.setCondition(request.condition());
        }
        if (request.level() != null) {
            badge.setLevel(request.level());
        }
        if (request.status() != null) {
            badge.setStatus(request.status());
        }
        badgeMapper.updateById(badge);

        return convertToVO(badge);
    }

    @Override
    @Transactional
    public void deleteBadge(Long badgeId) {
        badgeMapper.deleteById(badgeId);

        userBadgeMapper.delete(
                new LambdaQueryWrapper<UserBadge>()
                        .eq(UserBadge::getBadgeId, badgeId)
        );
    }

    @Override
    public Page<BadgeVO> listBadges(int page, int size) {
        LambdaQueryWrapper<Badge> wrapper = new LambdaQueryWrapper<Badge>()
                .orderByAsc(Badge::getLevel);

        Page<Badge> badgePage = badgeMapper.selectPage(new Page<>(page, size), wrapper);

        List<BadgeVO> voList = badgePage.getRecords().stream().map(this::convertToVO).toList();

        Page<BadgeVO> resultPage = new Page<>(badgePage.getCurrent(), badgePage.getSize(), badgePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    @Transactional
    public void grantBadge(Long userId, Long badgeId) {
        Long existing = userBadgeMapper.selectCount(
                new LambdaQueryWrapper<UserBadge>()
                        .eq(UserBadge::getUserId, userId)
                        .eq(UserBadge::getBadgeId, badgeId)
        );
        if (existing > 0) {
            throw new BusinessException("BADGE_ALREADY_GRANTED", "该用户已拥有此徽章");
        }

        UserBadge userBadge = new UserBadge();
        userBadge.setUserId(userId);
        userBadge.setBadgeId(badgeId);
        userBadgeMapper.insert(userBadge);
    }

    @Override
    @Transactional
    public void revokeBadge(Long userId, Long badgeId) {
        userBadgeMapper.delete(
                new LambdaQueryWrapper<UserBadge>()
                        .eq(UserBadge::getUserId, userId)
                        .eq(UserBadge::getBadgeId, badgeId)
        );
    }

    @Override
    public List<BadgeVO> getUserBadges(Long userId) {
        List<UserBadge> userBadges = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserBadge>()
                        .eq(UserBadge::getUserId, userId)
        );
        if (userBadges.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> badgeIds = userBadges.stream().map(UserBadge::getBadgeId).toList();
        return badgeMapper.selectBatchIds(badgeIds).stream()
                .map(this::convertToVO)
                .toList();
    }

    private BadgeVO convertToVO(Badge badge) {
        return new BadgeVO(
                badge.getId(),
                badge.getName(),
                badge.getIcon(),
                badge.getDescription(),
                badge.getCondition(),
                badge.getLevel(),
                badge.getStatus(),
                badge.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public void updateBadgeStatus(Long badgeId, Integer status) {
        Badge badge = badgeMapper.selectById(badgeId);
        if (badge == null) {
            throw new BusinessException("BADGE_NOT_FOUND", "徽章不存在");
        }
        badge.setStatus(status);
        badgeMapper.updateById(badge);
    }
}
