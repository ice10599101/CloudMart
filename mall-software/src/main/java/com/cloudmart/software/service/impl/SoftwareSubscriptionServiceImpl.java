package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.SoftwareSubscription;
import com.cloudmart.software.entity.SoftwareUserPreference;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareSubscriptionMapper;
import com.cloudmart.software.repository.SoftwareUserPreferenceMapper;
import com.cloudmart.software.service.SoftwareSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 订阅与偏好实现（方案 4.5，P1）。
 *
 * <p>不变量：新增订阅要求公开可见（隐藏软件不可订阅）；取消订阅只检查登录与关系
 * 归属，即使下架也幂等成功且不返回被隐藏软件的信息；偏好无记录视为开启。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareSubscriptionServiceImpl implements SoftwareSubscriptionService {

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareProperties softwareProperties;
    private final SoftwareSubscriptionMapper subscriptionMapper;
    private final SoftwareUserPreferenceMapper preferenceMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void subscribe(Long userId, Long softwareId) {
        // 11.2：P1 订阅独立开关，服务端生效（取消订阅保留，便于用户清理订阅关系）
        if (!softwareProperties.isSubscriptionEnabled()) {
            throw new BusinessException("SOFTWARE_SUBSCRIPTION_DISABLED", "订阅功能暂未开放");
        }
        if (accessPolicy.visiblePublishedRevision(softwareId) == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (isSubscribed(userId, softwareId)) {
            return; // 幂等
        }
        SoftwareSubscription subscription = new SoftwareSubscription();
        subscription.setSoftwareId(softwareId);
        subscription.setUserId(userId);
        subscription.setCreatedAt(LocalDateTime.now(clock));
        try {
            subscriptionMapper.insert(subscription);
        } catch (DuplicateKeyException e) {
            // 并发重复订阅：唯一键兜底，视为成功
            log.debug("并发重复订阅已收敛 userId={} softwareId={}", userId, softwareId);
        }
    }

    @Override
    public void unsubscribe(Long userId, Long softwareId) {
        // 幂等且不泄露状态：直接按归属删除，软件不存在/不可见同样成功
        subscriptionMapper.delete(new LambdaQueryWrapper<SoftwareSubscription>()
                .eq(SoftwareSubscription::getUserId, userId)
                .eq(SoftwareSubscription::getSoftwareId, softwareId));
    }

    @Override
    public boolean isSubscribed(Long userId, Long softwareId) {
        return subscriptionMapper.selectCount(new LambdaQueryWrapper<SoftwareSubscription>()
                .eq(SoftwareSubscription::getUserId, userId)
                .eq(SoftwareSubscription::getSoftwareId, softwareId)) > 0;
    }

    @Override
    public Map<String, Object> preferences(Long userId) {
        SoftwareUserPreference preference = preferenceMapper.selectOne(
                new LambdaQueryWrapper<SoftwareUserPreference>()
                        .eq(SoftwareUserPreference::getUserId, userId));
        return toView(userId, preference);
    }

    @Override
    @Transactional
    public Map<String, Object> updatePreferences(Long userId, Boolean updateNotifications,
                                                 Integer expectedVersion) {
        if (updateNotifications == null) {
            throw new BusinessException("SOFTWARE_PREFERENCE_INVALID", "缺少 updateNotifications");
        }
        SoftwareUserPreference preference = preferenceMapper.selectOne(
                new LambdaQueryWrapper<SoftwareUserPreference>()
                        .eq(SoftwareUserPreference::getUserId, userId));
        if (preference == null) {
            preference = new SoftwareUserPreference();
            preference.setUserId(userId);
            preference.setUpdateNotifications(updateNotifications);
            preference.setLockVersion(0);
            LocalDateTime now = LocalDateTime.now(clock);
            preference.setCreatedAt(now);
            preference.setUpdatedAt(now);
            preferenceMapper.insert(preference);
        } else {
            if (expectedVersion != null && !expectedVersion.equals(preference.getLockVersion())) {
                throw new BusinessException("SOFTWARE_VERSION_CONFLICT",
                        "偏好已被其他操作更新，请刷新");
            }
            if (!updateNotifications.equals(preference.getUpdateNotifications())) {
                preference.setUpdateNotifications(updateNotifications);
                preference.setLockVersion(preference.getLockVersion() + 1);
                preference.setUpdatedAt(LocalDateTime.now(clock));
                preferenceMapper.updateById(preference);
            }
        }
        return toView(userId, preference);
    }

    private Map<String, Object> toView(Long userId, SoftwareUserPreference preference) {
        Map<String, Object> view = new HashMap<>();
        view.put("updateNotifications", preference == null
                || !Boolean.FALSE.equals(preference.getUpdateNotifications()));
        view.put("lockVersion", preference == null ? 0 : preference.getLockVersion());
        return view;
    }

    /** SF05：合法平台/渠道集合（与服务端枚举对齐；非法值整单拒绝） */
    private static final java.util.Set<String> PLATFORMS = java.util.Set.of(
            "WINDOWS", "MACOS", "LINUX", "ANDROID", "IOS", "CROSS_PLATFORM", "WEB");
    private static final java.util.Set<String> CHANNELS = java.util.Set.of("STABLE", "BETA");

    @Override
    public java.util.Map<String, Object> updatePreference(Long userId, Long softwareId,
                                                          String platforms, String channels) {
        String safePlatforms = normalizeCsv(platforms, PLATFORMS, "platforms");
        String safeChannels = normalizeCsv(channels, CHANNELS, "channels");
        SoftwareSubscription subscription = subscriptionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareSubscription>()
                        .eq(SoftwareSubscription::getUserId, userId)
                        .eq(SoftwareSubscription::getSoftwareId, softwareId));
        if (subscription == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "尚未订阅该软件");
        }
        subscription.setPlatforms(safePlatforms);
        subscription.setChannels(safeChannels);
        subscriptionMapper.updateById(subscription);
        return preferenceView(safePlatforms, safeChannels);
    }

    @Override
    public java.util.Map<String, Object> preference(Long userId, Long softwareId) {
        SoftwareSubscription subscription = subscriptionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareSubscription>()
                        .eq(SoftwareSubscription::getUserId, userId)
                        .eq(SoftwareSubscription::getSoftwareId, softwareId));
        if (subscription == null) {
            return java.util.Map.of("subscribed", false);
        }
        return preferenceView(subscription.getPlatforms(), subscription.getChannels());
    }

    private java.util.Map<String, Object> preferenceView(String platforms, String channels) {
        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("subscribed", true);
        view.put("platforms", platforms == null ? java.util.List.of() : java.util.List.of(platforms.split(",")));
        view.put("channels", channels == null ? java.util.List.of() : java.util.List.of(channels.split(",")));
        return view;
    }

    /** SF05：限长去空白 + 白名单校验（空白=全部，返回 null） */
    private String normalizeCsv(String value, java.util.Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        java.util.List<String> items = java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
        if (items.size() > 10) {
            throw new BusinessException("SOFTWARE_SUBSCRIPTION_INVALID", field + " 最多 10 项");
        }
        for (String item : items) {
            if (!allowed.contains(item)) {
                throw new BusinessException("SOFTWARE_SUBSCRIPTION_INVALID",
                        field + " 含非法值：" + item);
            }
        }
        return String.join(",", items);
    }
}