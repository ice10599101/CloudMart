package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * mall-notification 内部客户端：宠物口吻提醒列表/未读数（复用现有通知表）。
 */
@FeignClient(name = "mall-notification", contextId = "petNotificationFeignClient",
        fallbackFactory = NotificationFeignClientFallbackFactory.class)
public interface NotificationFeignClient {

    /** 按类型查询当前用户通知（type=PET 为宠物口吻提醒；page/pageSize offset 分页） */
    @GetMapping("/internal/notifications")
    ApiResponse<List<NotificationItemVO>> listNotifications(@RequestParam("userId") Long userId,
                                                            @RequestParam(value = "type", required = false) String type,
                                                            @RequestParam("page") Integer page,
                                                            @RequestParam("pageSize") Integer pageSize);

    /** 未读数量（可按类型过滤） */
    @GetMapping("/internal/notifications/unread-count")
    ApiResponse<Long> getUnreadCount(@RequestParam("userId") Long userId,
                                     @RequestParam(value = "type", required = false) String type);

    /** 按类型全部已读（B19：type=PET 只清宠物提醒，不影响订单/评论等其他通知） */
    @PutMapping("/internal/notifications/read-all")
    ApiResponse<Long> markAllAsReadByType(@RequestParam("userId") Long userId,
                                          @RequestParam("type") String type);

    /** 私信未读总数（原文档 §28.3 私信提醒触发依据；来自 conversations 未读字段聚合） */
    @GetMapping("/internal/chat/unread-count")
    ApiResponse<Long> getUnreadChatCount(@RequestParam("userId") Long userId);

    /** 通知条目（宠物模块消费的字段子集） */
    record NotificationItemVO(
            Long id,
            String type,
            String title,
            String content,
            Long bizId,
            String bizType,
            Boolean isRead,
            String createdAt
    ) {
    }
}
