package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.NotificationPreferenceUpdateRequest;
import com.cloudmart.wish.entity.WishNotificationPreference;
import com.cloudmart.wish.enums.NotificationChannel;
import com.cloudmart.wish.enums.WishNotificationType;
import com.cloudmart.wish.repository.WishNotificationPreferenceMapper;
import com.cloudmart.wish.service.NotificationPreferenceService;
import com.cloudmart.wish.vo.NotificationPreferenceMatrixVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户通知偏好服务实现（文档 2.14，Sprint 2.5）。
 *
 * <p>语义：无记录 = 默认开启；仅显式 enabled=false 的记录才关闭。
 * 一键关闭所有提醒由前端对全部类型批量 PUT enabled=false 实现，
 * 服务端不做特殊"全关"标记（矩阵语义天然覆盖）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final WishNotificationPreferenceMapper preferenceMapper;

    @Override
    public NotificationPreferenceMatrixVO getMatrix(Long userId) {
        Map<String, Map<NotificationChannel, Boolean>> overrides = loadOverrides(userId);
        List<NotificationPreferenceMatrixVO.PreferenceItemVO> items = Arrays.stream(WishNotificationType.values())
                .map(type -> new NotificationPreferenceMatrixVO.PreferenceItemVO(
                        type.name(), buildChannels(type.name(), overrides)))
                .toList();
        return new NotificationPreferenceMatrixVO(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationPreferenceMatrixVO updateMatrix(Long userId, NotificationPreferenceUpdateRequest request) {
        for (NotificationPreferenceUpdateRequest.UpdateItem item : request.updates()) {
            requireValidType(item.type());
            upsert(userId, item.type(), item.channel(), item.enabled());
        }
        log.info("更新通知偏好, userId={}, count={}", userId, request.updates().size());
        return getMatrix(userId);
    }

    @Override
    public boolean isChannelEnabled(Long userId, String notificationType, NotificationChannel channel) {
        WishNotificationPreference record = preferenceMapper.selectOne(
                new LambdaQueryWrapper<WishNotificationPreference>()
                        .eq(WishNotificationPreference::getUserId, userId)
                        .eq(WishNotificationPreference::getNotificationType, notificationType)
                        .eq(WishNotificationPreference::getChannel, channel)
                        .last("LIMIT 1"));
        // 无记录 = 默认开启
        return record == null || Boolean.TRUE.equals(record.getEnabled());
    }

    /**
     * 加载用户全部显式偏好记录：type → (channel → enabled)。
     */
    private Map<String, Map<NotificationChannel, Boolean>> loadOverrides(Long userId) {
        return preferenceMapper.selectList(new LambdaQueryWrapper<WishNotificationPreference>()
                        .eq(WishNotificationPreference::getUserId, userId))
                .stream()
                .collect(Collectors.groupingBy(
                        WishNotificationPreference::getNotificationType,
                        Collectors.toMap(
                                WishNotificationPreference::getChannel,
                                preference -> Boolean.TRUE.equals(preference.getEnabled()),
                                (first, second) -> second)));
    }

    private Map<NotificationChannel, Boolean> buildChannels(String type,
                                                            Map<String, Map<NotificationChannel, Boolean>> overrides) {
        Map<NotificationChannel, Boolean> channels = new EnumMap<>(NotificationChannel.class);
        Map<NotificationChannel, Boolean> typeOverrides = overrides.getOrDefault(type, Map.of());
        for (NotificationChannel channel : NotificationChannel.values()) {
            // 无记录默认开启
            channels.put(channel, typeOverrides.getOrDefault(channel, true));
        }
        return channels;
    }

    private void upsert(Long userId, String type, NotificationChannel channel, Boolean enabled) {
        WishNotificationPreference existing = preferenceMapper.selectOne(
                new LambdaQueryWrapper<WishNotificationPreference>()
                        .eq(WishNotificationPreference::getUserId, userId)
                        .eq(WishNotificationPreference::getNotificationType, type)
                        .eq(WishNotificationPreference::getChannel, channel)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setEnabled(enabled);
            preferenceMapper.updateById(existing);
        } else {
            WishNotificationPreference preference = new WishNotificationPreference();
            preference.setUserId(userId);
            preference.setNotificationType(type);
            preference.setChannel(channel);
            preference.setEnabled(enabled);
            preferenceMapper.insert(preference);
        }
    }

    private void requireValidType(String type) {
        boolean valid = Arrays.stream(WishNotificationType.values())
                .anyMatch(notificationType -> notificationType.name().equals(type));
        if (!valid) {
            throw new BusinessException(WishErrorCodes.WISH_NOTIFICATION_TYPE_INVALID,
                    "通知类型非法: " + type);
        }
    }
}
