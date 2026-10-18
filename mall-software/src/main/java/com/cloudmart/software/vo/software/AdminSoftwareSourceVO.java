package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;

/**
 * SW06：后台来源管理视图（按业务版本/平台/架构）。
 *
 * <p>默认脱敏：url 只保留协议与主机；提取码/解压密码不进入该视图，禁止导出。</p>
 */
public record AdminSoftwareSourceVO(
        String releaseKey,
        String versionName,
        String channel,
        String sourceKey,
        String sourceType,
        String platform,
        String arch,
        String label,
        String maskedUrl,
        String availability,
        String statusReason,
        String checkedFingerprint,
        LocalDateTime checkedAt) {
}
