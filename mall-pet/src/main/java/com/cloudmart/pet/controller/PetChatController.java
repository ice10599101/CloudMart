package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.PetChatRequest;
import com.cloudmart.pet.service.PetChatService;
import com.cloudmart.pet.vo.PetChatMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 宠物聊天接口（三层结构 + 白名单上下文 + 结构化记忆 + 人格，原文档 §21-27/§59）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物聊天", description = "AI 陪伴聊天、聊天历史")
@RequiredArgsConstructor
public class PetChatController {

    private final PetChatService chatService;

    @PostMapping("/chat")
    @Operation(summary = "和宠物聊天", description = "每日 20 次上限（429 PET_AI_RATE_LIMITED）；"
            + "危机词本地拦截安抚；AI 故障降级模板回复（isAiReply=false）")
    @SentinelResource("PET_CHAT")
    public ApiResponse<PetChatMessageVO> chat(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody PetChatRequest request) {
        return ApiResponse.ok(chatService.chat(userId, request));
    }

    @GetMapping("/chat/history")
    @Operation(summary = "聊天历史", description = "cursor 分页（messageId 倒序）；单页 ≤50 条")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetChatMessageVO>> history(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "上一页最后一条消息 ID（首页不传）")
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(chatService.history(userId, cursor, pageSize));
    }
}
