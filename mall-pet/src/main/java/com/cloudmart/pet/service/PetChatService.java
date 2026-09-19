package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.PetChatRequest;
import com.cloudmart.pet.vo.PetChatMessageVO;

import java.util.List;

/**
 * 宠物聊天服务（三层结构：固定行为 → 宠物/社区状态 → AI 生成，原文档 §22-26）。
 */
public interface PetChatService {

    /**
     * 发送消息并获取宠物回复。
     * 固定意图/危机词走本地模板（不调 AI）；其余组装白名单上下文调 AI，
     * AI 不可用时降级模板回复（isAiReply=false，陪伴体验不中断）。
     */
    PetChatMessageVO chat(Long userId, PetChatRequest request);

    /** 聊天历史（cursor 分页：messageId 倒序，cursor 为上一页最后一条 ID） */
    List<PetChatMessageVO> history(Long userId, Long cursor, Integer pageSize);
}
