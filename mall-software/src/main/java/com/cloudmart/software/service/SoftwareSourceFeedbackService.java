package com.cloudmart.software.service;

import com.cloudmart.software.vo.software.SoftwareFeedbackVO;

/**
 * 链接失效反馈（方案 5.3/6.9）：同指纹同人去重；24h 3 账号标 SUSPECTED 并通知分享者。
 */
public interface SoftwareSourceFeedbackService {

    /**
     * @return 反馈结果（重复提交返回现有反馈；不重复创建）
     */
    SoftwareFeedbackVO feedback(Long userId, Long softwareId, Long sourceId,
                                String reasonCode, String description);
}
