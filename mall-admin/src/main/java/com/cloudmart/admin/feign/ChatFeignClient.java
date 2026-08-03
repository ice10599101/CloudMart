package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(contextId = "chatFeignClient", name = "mall-notification", path = "/admin/chat", fallbackFactory = ChatFeignClientFallbackFactory.class)
public interface ChatFeignClient {

    @GetMapping("/conversations")
    ApiResponse<List<Map<String, Object>>> listConversations(@RequestParam("page") Integer page,
                                                             @RequestParam("pageSize") Integer pageSize);

    @GetMapping("/conversations/{conversationId}/messages")
    ApiResponse<List<Map<String, Object>>> listMessages(@PathVariable("conversationId") Long conversationId,
                                                        @RequestParam("page") Integer page,
                                                        @RequestParam("pageSize") Integer pageSize);

    @GetMapping("/stats")
    ApiResponse<Map<String, Long>> getChatStats();
}
