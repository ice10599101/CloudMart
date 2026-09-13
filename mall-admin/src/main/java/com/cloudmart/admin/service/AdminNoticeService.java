package com.cloudmart.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminNoticeRequest;
import com.cloudmart.admin.dto.AdminNoticeResponse;

import java.util.List;

public interface AdminNoticeService {

    Page<AdminNoticeResponse> page(String noticeTitle, Integer noticeType, Integer page, Integer pageSize);

    AdminNoticeResponse getById(Long id);

    void create(AdminNoticeRequest request);

    void update(Long id, AdminNoticeRequest request);

    void delete(Long id);

    void markAsRead(Long noticeId, Long userId);

    List<AdminNoticeResponse> unreadList(Long userId);

    void updateStatus(Long id, Integer status);

    /**
     * 将公告一键推送为全站用户的系统通知（经 mall-notification 广播）。
     * 仅允许推送已启用（status=1）的公告。
     */
    void pushToAllUsers(Long id);
}
