package com.cloudmart.wish.enums;

/**
 * Prompt 模板状态（版本生命周期）。
 */
public enum AiPromptStatus {
    /** 草稿（不可被运行时选中） */
    DRAFT,
    /** 生效中（参与运行时选取/A-B 分流） */
    ACTIVE,
    /** 已归档（历史版本，仅管理后台可查） */
    ARCHIVED
}
