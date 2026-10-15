package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSubscription;
import com.cloudmart.software.entity.SoftwareUserPreference;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareSubscriptionMapper;
import com.cloudmart.software.repository.SoftwareUserPreferenceMapper;
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
 * P1 订阅与偏好单测（4.5）：新增订阅要求可见、重复订阅幂等；
 * 取消订阅对不可见软件同样成功（不泄露状态）；偏好缺省开启、CAS 校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareSubscriptionServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long SOFTWARE_ID = 100L;

    @Mock
    private SoftwareAccessPolicy accessPolicy;
    @Mock
    private SoftwareProperties softwareProperties;
    @Mock
    private SoftwareSubscriptionMapper subscriptionMapper;
    @Mock
    private SoftwareUserPreferenceMapper preferenceMapper;

    private SoftwareSubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(softwareProperties.isSubscriptionEnabled()).thenReturn(true);
        service = new SoftwareSubscriptionServiceImpl(accessPolicy, softwareProperties,
                subscriptionMapper, preferenceMapper,
                Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("订阅：可见软件插入；已订阅则幂等跳过")
    void subscribe() {
        SoftwareRevision published = new SoftwareRevision();
        published.setId(501L);
        published.setSoftwareId(SOFTWARE_ID);
        published.setReviewStatus(SoftwareReviewStatus.APPROVED);
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(published);
        when(subscriptionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.subscribe(USER_ID, SOFTWARE_ID);
        verify(subscriptionMapper).insert(any(SoftwareSubscription.class));

        when(subscriptionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        service.subscribe(USER_ID, SOFTWARE_ID);
        verify(subscriptionMapper).insert(any(SoftwareSubscription.class)); // 仍为 1 次
    }

    @Test
    @DisplayName("订阅：不可见软件拒绝（不泄露状态）")
    void subscribe_invisibleRejected() {
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.subscribe(USER_ID, SOFTWARE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("取消订阅：只按归属删除，软件下架也成功")
    void unsubscribe_alwaysSucceeds() {
        service.unsubscribe(USER_ID, SOFTWARE_ID);

        verify(subscriptionMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("偏好：无记录视为开启；首建写入；CAS 冲突拒绝")
    void preferences() {
        when(preferenceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(service.preferences(USER_ID)).containsEntry("updateNotifications", true);

        var created = service.updatePreferences(USER_ID, false, null);
        verify(preferenceMapper).insert(any(SoftwareUserPreference.class));
        assertThat(created).containsEntry("updateNotifications", false);

        SoftwareUserPreference existing = new SoftwareUserPreference();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setUpdateNotifications(true);
        existing.setLockVersion(2);
        when(preferenceMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> service.updatePreferences(USER_ID, false, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");

        var updated = service.updatePreferences(USER_ID, false, 2);
        assertThat(updated).containsEntry("updateNotifications", false);
        verify(preferenceMapper).updateById(existing);
    }

    @Test
    @DisplayName("11.2：P1 订阅开关关闭时新增订阅被拒（取消订阅保留可用）")
    void subscribe_disabledBySwitch() {
        when(softwareProperties.isSubscriptionEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.subscribe(USER_ID, SOFTWARE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SUBSCRIPTION_DISABLED");
        verify(subscriptionMapper, never()).insert(any(SoftwareSubscription.class));

        // 取消订阅不依赖开关：清理订阅关系始终可用（软件下架同样幂等成功）
        service.unsubscribe(USER_ID, SOFTWARE_ID);
        verify(subscriptionMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("偏好：updateNotifications 缺失拒绝")
    void preferences_missingField() {
        assertThatThrownBy(() -> service.updatePreferences(USER_ID, null, null))
                .isInstanceOf(BusinessException.class);
        verify(preferenceMapper, never()).insert(any(SoftwareUserPreference.class));
    }
}
