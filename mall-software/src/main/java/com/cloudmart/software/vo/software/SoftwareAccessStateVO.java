package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录用户查看条件判定结果（方案 7.3 access-state）：不返回真实来源；
 * no-store——旧结果不能绕过 resolve。
 */
public record SoftwareAccessStateVO(
        Long softwareId,
        String accessMode,
        boolean allowed,
        String reasonCode,
        Integer currentLevel,
        Integer requiredLevel,
        LocalDateTime grantExpiresAt,
        /** 付款模式展示联系入口（无论 allowed，供 403 页面"联系管理员"） */
        List<SoftwareAccessPolicyVO.ContactVO> contacts) {
}
