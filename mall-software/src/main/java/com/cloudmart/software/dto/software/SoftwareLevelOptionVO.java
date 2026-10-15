package com.cloudmart.software.dto.software;

/**
 * 启用中的社区等级选项（方案 9.6）：发布表单选择最低等级用。
 */
public record SoftwareLevelOptionVO(
        Integer level,
        String title) {
}
