package com.cloudmart.software.service;

import java.util.Map;

/**
 * 软件更新订阅与提醒偏好（方案 4.5，P1）：与收藏分开，仅接收新增版本通知；
 * 新增订阅要求公开可见，取消订阅即使下架也幂等成功；偏好缺省开启。
 */
public interface SoftwareSubscriptionService {

    /** 订阅（幂等；要求公开可见） */
    void subscribe(Long userId, Long softwareId);

    /** 取消订阅（幂等；软件不可见也成功，不泄露状态） */
    void unsubscribe(Long userId, Long softwareId);

    /** 是否已订阅（详情个性化用） */
    boolean isSubscribed(Long userId, Long softwareId);

    /** 更新提醒偏好（缺省开启） */
    Map<String, Object> preferences(Long userId);

    /** 修改偏好（expectedVersion 非空时 CAS；无记录首建） */
    Map<String, Object> updatePreferences(Long userId, Boolean updateNotifications,
                                          Integer expectedVersion);
}
