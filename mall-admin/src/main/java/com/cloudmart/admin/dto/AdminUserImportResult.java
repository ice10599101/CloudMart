package com.cloudmart.admin.dto;

import java.util.List;

/**
 * 批量导入结果（SEC-03）：initialCredentials 为本次导入生成的每人独立随机初始
 * 密码（username=password 形式），仅在导入响应中一次性展示，禁止再使用统一
 * 固定初始密码。
 */
public record AdminUserImportResult(
    int successCount,
    int failureCount,
    List<String> failureMessages,
    List<String> initialCredentials
) {}
