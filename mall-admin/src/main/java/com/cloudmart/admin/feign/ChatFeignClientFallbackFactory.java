package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ChatFeignClientFallbackFactory implements FallbackFactory<ChatFeignClient> {

    @Override
    public ChatFeignClient create(Throwable cause) {
        log.error("聊天服务调用失败: {}", cause.getMessage());
        return new ChatFeignClient() {
            @Override
            public ApiResponse<List<Map<String, Object>>> listConversations(Integer page, Integer pageSize) {
                throw new BusinessException("CHAT_SERVICE_UNAVAILABLE", "聊天服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<List<Map<String, Object>>> listMessages(Long conversationId, Integer page, Integer pageSize) {
                throw new BusinessException("CHAT_SERVICE_UNAVAILABLE", "聊天服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Map<String, Long>> getChatStats() {
                throw new BusinessException("CHAT_SERVICE_UNAVAILABLE", "聊天服务不可用，请稍后重试");
            }
        };
    }
}
