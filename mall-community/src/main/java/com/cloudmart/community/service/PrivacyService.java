package com.cloudmart.community.service;

import com.cloudmart.community.vo.PrivacyVisibility;

/**
 * 用户资料字段可见性服务。
 *
 * <p>根据查看者与目标用户的关系（本人 / 互关好友 / 陌生人）
 * 及目标用户的可见性设置，计算生日、邮箱两个敏感字段是否可见。</p>
 */
public interface PrivacyService {

    /**
     * 计算目标用户资料字段对查看者的可见性。
     *
     * @param viewerUserId 查看者用户 ID（可为 null，null 视为陌生人）
     * @param targetUserId 目标用户 ID
     * @return 生日、邮箱可见性结果
     */
    PrivacyVisibility checkVisibility(Long viewerUserId, Long targetUserId);
}