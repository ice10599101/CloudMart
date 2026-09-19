package com.cloudmart.pet.service.impl;

/**
 * 宠物聊天 AI 客户端接口（便于单测 Mock 与未来切换多模型路由）。
 */
public interface PetAiClient {

    /**
     * 生成宠物回复。
     *
     * @param systemPrompt 人格 + 白名单上下文 + 行为约束
     * @param userMessage  用户消息（含最近对话历史的拼接视图由调用方组装）
     * @return 宠物回复文本
     */
    String generateReply(String systemPrompt, String userMessage);
}
