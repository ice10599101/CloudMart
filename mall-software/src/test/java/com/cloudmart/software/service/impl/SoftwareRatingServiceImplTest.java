package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRating;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRatingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 评分单测（4.5）：首建计数+1、改分仅 sum 变化、撤销计数-1、
 * 分享者自评拒绝、版本冲突。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareRatingServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long OWNER_ID = 7L;
    private static final Long SOFTWARE_ID = 100L;

    @Mock
    private SoftwareAccessPolicy accessPolicy;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareRatingMapper ratingMapper;

    private SoftwareRatingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SoftwareRatingServiceImpl(accessPolicy, softwareMapper, ratingMapper,
                Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.systemDefault()));
        SoftwareRevision published = new SoftwareRevision();
        published.setId(501L);
        published.setSoftwareId(SOFTWARE_ID);
        published.setReviewStatus(SoftwareReviewStatus.APPROVED);
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(published);
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(OWNER_ID);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        software.setRatingCount(0);
        software.setRatingSum(0);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
    }

    @Test
    @DisplayName("首次评分：插入 + 计数 count+1/sum+score；返回聚合")
    void rate_firstTime() {
        when(ratingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        // aggregate 读回的软件行（模拟计数已递增后的落库值）
        Software after = new Software();
        after.setId(SOFTWARE_ID);
        after.setOwnerId(OWNER_ID);
        after.setRatingCount(1);
        after.setRatingSum(4);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(after);

        var result = service.rate(USER_ID, SOFTWARE_ID, 4, null);

        verify(ratingMapper).insert(any(SoftwareRating.class));
        verify(softwareMapper).incrementRating(SOFTWARE_ID, 1, 4);
        assertThat(result.ratingCount()).isEqualTo(1);
        assertThat(result.ratingAvg()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("重复同分：无写操作（幂等）")
    void rate_sameScoreNoop() {
        SoftwareRating existing = new SoftwareRating();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setSoftwareId(SOFTWARE_ID);
        existing.setScore(4);
        existing.setLockVersion(0);
        when(ratingMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        service.rate(USER_ID, SOFTWARE_ID, 4, null);

        verify(ratingMapper, never()).updateById(any(SoftwareRating.class));
        verify(softwareMapper, never()).incrementRating(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("改分：仅 sum 变化；版本冲突拒绝")
    void rate_changeScoreAndVersionConflict() {
        SoftwareRating existing = new SoftwareRating();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setSoftwareId(SOFTWARE_ID);
        existing.setScore(2);
        existing.setLockVersion(3);
        when(ratingMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> service.rate(USER_ID, SOFTWARE_ID, 5, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");

        service.rate(USER_ID, SOFTWARE_ID, 5, 3);
        verify(softwareMapper).incrementRating(SOFTWARE_ID, 0, 3);
        assertThat(existing.getScore()).isEqualTo(5);
        assertThat(existing.getLockVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("撤销：计数 count-1/sum-score；无记录幂等")
    void revoke() {
        SoftwareRating existing = new SoftwareRating();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setSoftwareId(SOFTWARE_ID);
        existing.setScore(5);
        existing.setLockVersion(1);
        when(ratingMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        service.revoke(USER_ID, SOFTWARE_ID, null);
        verify(ratingMapper).deleteById(1L);
        verify(softwareMapper).incrementRating(SOFTWARE_ID, -1, -5);

        when(ratingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        service.revoke(USER_ID, SOFTWARE_ID, null); // 幂等
    }

    @Test
    @DisplayName("分享者自评拒绝；分值越界拒绝")
    void guards() {
        assertThatThrownBy(() -> service.rate(OWNER_ID, SOFTWARE_ID, 5, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分享者");

        assertThatThrownBy(() -> service.rate(USER_ID, SOFTWARE_ID, 6, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1~5");
    }
}
