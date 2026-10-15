package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;

/** 创建软件结果（方案 7.3）：只含 ID 与版本号 */
public record SoftwareCreatedVO(
        Long softwareId,
        Long workingRevisionId,
        Integer lockVersion) {
}
