package com.cloudmart.community.vo.software;

/**
 * 启用中的社区等级选项（方案 9.6）：供软件发布表单选择最低等级，
 * 可短缓存 60 秒展示，提交时以 resolve/审核侧实时校验为准。
 */
public record SoftwareLevelOptionVO(
        Integer level,
        String title) {
}
