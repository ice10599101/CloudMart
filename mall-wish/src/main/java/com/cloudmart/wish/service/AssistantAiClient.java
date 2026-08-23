package com.cloudmart.wish.service;

import com.cloudmart.wish.service.impl.GoalBreakdownParser;

/**
 * AI 心愿助手客户端（目标拆解，Sprint 2.5）。
 */
public interface AssistantAiClient {

    /**
     * 调用 LLM 生成意图分析 + 目标拆解。
     *
     * @param systemPrompt 系统提示词（Prompt 模板服务下发）
     * @param userText     用户目标描述（已脱敏）
     * @return 解析后的拆解结果（goals 为空表示输出不可用，调用方降级）
     */
    GoalBreakdownParser.ParsedBreakdown generateBreakdown(String systemPrompt, String userText);

    /**
     * 调用 LLM 生成纯文本（预期管理引导文案/年度报告总结）。
     *
     * @param systemPrompt 系统提示词
     * @param userText     输入上下文
     * @return 模型输出文本（失败时抛 WISH_AI_UNAVAILABLE）
     */
    String generateText(String systemPrompt, String userText);
}
