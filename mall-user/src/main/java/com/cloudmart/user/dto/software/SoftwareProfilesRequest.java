package com.cloudmart.user.dto.software;

import java.util.List;

/**
 * 小答软件批量资料请求（方案 9.6）：上限 100 个 ID（与现有批量查询一致）。
 */
public record SoftwareProfilesRequest(List<Long> userIds) {
}
