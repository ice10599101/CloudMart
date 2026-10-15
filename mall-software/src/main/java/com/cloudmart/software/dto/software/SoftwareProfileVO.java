package com.cloudmart.software.dto.software;

/**
 * 用户最小资料（方案 9.6）：仅昵称/头像/账号可用性；缺失 ID 不在结果中，
 * 展示侧据此降级为通用头像/昵称。
 */
public record SoftwareProfileVO(
        Long id,
        String nickname,
        String avatar,
        boolean accountAvailable) {
}
