package com.cloudmart.wish.enums;

/**
 * Prompt 模板场景（Sprint 2.5，文档 2.5 管理后台 Prompt 管理）。
 *
 * <p>与 {@link AiScene} 的差异：AiScene 描述对话记录业务场景，
 * 本枚举描述 Prompt 模板管理维度——额外包含预期管理引导
 * （EXPECTED_GUIDE，引导文案不入对话表）。</p>
 */
public enum AiPromptScene {
    /** 目标拆解 Prompt */
    GOAL_BREAKDOWN,
    /** 树洞治愈 Prompt */
    TREE_HOLE,
    /** 年度报告 growthSummary 生成 Prompt */
    ANNUAL_REPORT,
    /** 预期管理到期引导文案 Prompt */
    EXPECTED_GUIDE
}
