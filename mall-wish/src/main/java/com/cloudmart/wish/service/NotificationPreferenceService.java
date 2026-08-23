package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.NotificationPreferenceUpdateRequest;
import com.cloudmart.wish.vo.NotificationPreferenceMatrixVO;

/**
 * 用户通知偏好服务（文档 2.14，Sprint 2.5）。
 *
 * <p>偏好矩阵：13 通知类型 × 4 渠道（PUSH/SMS/EMAIL/IN_APP）。
 * <b>无记录视为默认开启</b>；一键关闭所有提醒 = 对全部提醒类型
 * 全渠道写入 enabled=false（前端批量 PUT 实现）。</p>
 */
public interface NotificationPreferenceService {

    /**
     * 获取用户完整偏好矩阵（含默认开启项）。
     */
    NotificationPreferenceMatrixVO getMatrix(Long userId);

    /**
     * 批量更新偏好（逐项 upsert）。
     *
     * @return 更新条数 + 完整矩阵
     * @throws BusinessException WISH_NOTIFICATION_TYPE_INVALID 通知类型非法
     */
    NotificationPreferenceMatrixVO updateMatrix(Long userId, NotificationPreferenceUpdateRequest request);

    /**
     * 判断指定类型+渠道是否允许推送（无记录=默认开启）。
     * 供预期管理/陪伴提醒等推送前过滤使用。
     */
    boolean isChannelEnabled(Long userId, String notificationType,
                             com.cloudmart.wish.enums.NotificationChannel channel);
}
