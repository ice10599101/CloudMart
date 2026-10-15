package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRating;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRatingMapper;
import com.cloudmart.software.service.SoftwareRatingService;
import com.cloudmart.software.vo.software.SoftwareRatingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 评分实现（方案 4.5，P1）。
 *
 * <p>不变量：先经公开可见谓词（隐藏软件不可评分）；分享者本人拒绝；评分行与
 * software.rating_count/rating_sum 同事务；UNIQUE(user_id,software_id) 兜底并发首建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareRatingServiceImpl implements SoftwareRatingService {

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareMapper softwareMapper;
    private final SoftwareRatingMapper ratingMapper;
    private final Clock clock;

    @Override
    @Transactional
    public SoftwareRatingVO rate(Long userId, Long softwareId, Integer score,
                                 Integer expectedVersion) {
        if (score == null || score < 1 || score > 5) {
            throw new BusinessException("SOFTWARE_RATING_INVALID", "评分须为 1~5 星");
        }
        requireRatable(userId, softwareId);
        SoftwareRating existing = ratingMapper.selectOne(new LambdaQueryWrapper<SoftwareRating>()
                .eq(SoftwareRating::getUserId, userId)
                .eq(SoftwareRating::getSoftwareId, softwareId));

        if (existing == null) {
            SoftwareRating rating = new SoftwareRating();
            rating.setSoftwareId(softwareId);
            rating.setUserId(userId);
            rating.setScore(score);
            rating.setLockVersion(0);
            LocalDateTime now = LocalDateTime.now(clock);
            rating.setCreatedAt(now);
            rating.setUpdatedAt(now);
            ratingMapper.insert(rating);
            softwareMapper.incrementRating(softwareId, 1, score);
        } else {
            requireVersion(existing, expectedVersion);
            if (!score.equals(existing.getScore())) {
                int delta = score - existing.getScore();
                existing.setScore(score);
                existing.setLockVersion(existing.getLockVersion() + 1);
                existing.setUpdatedAt(LocalDateTime.now(clock));
                ratingMapper.updateById(existing);
                softwareMapper.incrementRating(softwareId, 0, delta);
            }
        }
        return aggregate(softwareId, userId);
    }

    @Override
    @Transactional
    public SoftwareRatingVO revoke(Long userId, Long softwareId, Integer expectedVersion) {
        SoftwareRating existing = ratingMapper.selectOne(new LambdaQueryWrapper<SoftwareRating>()
                .eq(SoftwareRating::getUserId, userId)
                .eq(SoftwareRating::getSoftwareId, softwareId));
        if (existing != null) {
            requireVersion(existing, expectedVersion);
            ratingMapper.deleteById(existing.getId());
            softwareMapper.incrementRating(softwareId, -1, -existing.getScore());
        }
        return aggregate(softwareId, userId);
    }

    @Override
    public Integer myScore(Long userId, Long softwareId) {
        SoftwareRating rating = ratingMapper.selectOne(new LambdaQueryWrapper<SoftwareRating>()
                .eq(SoftwareRating::getUserId, userId)
                .eq(SoftwareRating::getSoftwareId, softwareId));
        return rating == null ? null : rating.getScore();
    }

    /** 评分前置：公开可见 + 非分享者本人（不满足统一不泄露状态） */
    private void requireRatable(Long userId, Long softwareId) {
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        Software software = softwareMapper.selectById(softwareId);
        if (software == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (software.getOwnerId().equals(userId)) {
            throw new BusinessException("SOFTWARE_FORBIDDEN", "分享者不能给自己的软件评分");
        }
    }

    private void requireVersion(SoftwareRating rating, Integer expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(rating.getLockVersion())) {
            throw new BusinessException("SOFTWARE_VERSION_CONFLICT", "评分已被其他操作更新，请刷新");
        }
    }

    private SoftwareRatingVO aggregate(Long softwareId, Long userId) {
        Software software = softwareMapper.selectById(softwareId);
        Integer count = software == null || software.getRatingCount() == null
                ? 0 : software.getRatingCount();
        Integer sum = software == null || software.getRatingSum() == null
                ? 0 : software.getRatingSum();
        Double avg = count == 0 ? null : Math.round(sum * 10.0 / count) / 10.0;
        return new SoftwareRatingVO(softwareId, myScore(userId, softwareId), count, avg);
    }
}
