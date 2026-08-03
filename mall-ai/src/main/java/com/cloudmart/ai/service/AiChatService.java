package com.cloudmart.ai.service;

import com.cloudmart.ai.dto.ChatRequest;
import com.cloudmart.ai.dto.ChatResponse;

public interface AiChatService {

    /**
     * 与 AI 导购助手对话，支持多轮对话和降级策略。
     * 当 LLM 不可用时，降级为基于关键词的常规搜索。
     */
    ChatResponse chat(Long userId, ChatRequest request);
}
