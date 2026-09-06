package com.cloudmart.wish.service;

import com.cloudmart.wish.service.impl.TreeHoleReplyParser;

/**
 * 树洞 AI 客户端抽象（Spring AI ChatClient / OpenAI 兼容协议，文档 30.1）。
 *
 * <p>接口隔离便于单元测试 mock 与未来更换模型供应商；
 * 实现见 {@code RemoteTreeHoleAiClient}。</p>
 */
public interface TreeHoleAiClient {

    /**
     * 生成树洞治愈回复。
     *
     * @param systemPrompt 系统 Prompt（JSON 输出契约见 WishAiProperties）
     * @param userMessage  已脱敏的用户消息
     * @return 解析后的回复（reply / sentimentScore / resources）
     * @throws com.cloudmart.common.exception.BusinessException WISH_AI_UNAVAILABLE
     *         重试后仍失败（503）
     */
    TreeHoleReplyParser.ParsedReply generateReply(String systemPrompt, String userMessage);
}
