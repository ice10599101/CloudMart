package com.cloudmart.wish.enums;

/**
 * 还愿内容流转状态（Sprint 2.7，文档 2.7 内容流转测试）。
 *
 * <p>SUCCESS=community 帖子已生成；FAILED=重试 3 次后仍失败（管理端可重试）；
 * HIDDEN=还愿故事删除后帖子已同步隐藏（状态同步规则）。</p>
 */
public enum ContentFlowStatus {
    SUCCESS,
    FAILED,
    HIDDEN
}
