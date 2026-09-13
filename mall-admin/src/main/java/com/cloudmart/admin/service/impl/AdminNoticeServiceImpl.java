package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminNoticeRequest;
import com.cloudmart.admin.dto.AdminNoticeResponse;
import com.cloudmart.admin.entity.AdminNotice;
import com.cloudmart.admin.entity.AdminNoticeRead;
import com.cloudmart.admin.dto.feign.BroadcastNotificationRequest;
import com.cloudmart.admin.feign.NotificationFeignClient;
import com.cloudmart.admin.repository.AdminNoticeMapper;
import com.cloudmart.admin.repository.AdminNoticeReadMapper;
import com.cloudmart.admin.service.AdminNoticeService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminNoticeServiceImpl implements AdminNoticeService {

    /** 推送到用户消息中心的通知类型：SYSTEM 归入消息中心"系统通知"分组 */
    private static final String PUSH_NOTIFICATION_TYPE = "SYSTEM";

    private final AdminNoticeMapper adminNoticeMapper;
    private final AdminNoticeReadMapper adminNoticeReadMapper;
    private final NotificationFeignClient notificationFeignClient;

    public AdminNoticeServiceImpl(AdminNoticeMapper adminNoticeMapper,
                                  AdminNoticeReadMapper adminNoticeReadMapper,
                                  NotificationFeignClient notificationFeignClient) {
        this.adminNoticeMapper = adminNoticeMapper;
        this.adminNoticeReadMapper = adminNoticeReadMapper;
        this.notificationFeignClient = notificationFeignClient;
    }

    @Override
    public Page<AdminNoticeResponse> page(String noticeTitle, Integer noticeType, Integer page, Integer pageSize) {
        int effectivePage = page != null ? page : 1;
        int effectivePageSize = pageSize != null ? pageSize : 20;

        Page<AdminNotice> mpPage = new Page<>(effectivePage, effectivePageSize);
        LambdaQueryWrapper<AdminNotice> wrapper = new LambdaQueryWrapper<AdminNotice>()
                .like(noticeTitle != null && !noticeTitle.isBlank(), AdminNotice::getNoticeTitle, noticeTitle)
                .eq(noticeType != null, AdminNotice::getNoticeType, noticeType)
                .orderByDesc(AdminNotice::getCreatedAt);

        Page<AdminNotice> result = adminNoticeMapper.selectPage(mpPage, wrapper);

        Page<AdminNoticeResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(n -> toResponse(n, null)).toList());
        return responsePage;
    }

    @Override
    public AdminNoticeResponse getById(Long id) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("NOTICE_NOT_FOUND", "通知公告不存在");
        }

        Long readCount = adminNoticeReadMapper.selectCount(
                new LambdaQueryWrapper<AdminNoticeRead>().eq(AdminNoticeRead::getNoticeId, id)
        );

        return new AdminNoticeResponse(
                notice.getId(),
                notice.getNoticeTitle(),
                notice.getNoticeType(),
                notice.getNoticeContent(),
                notice.getStatus(),
                notice.getRemark(),
                notice.getCreatedAt(),
                readCount,
                null
        );
    }

    @Override
    @Transactional
    public void create(AdminNoticeRequest request) {
        AdminNotice notice = new AdminNotice();
        notice.setNoticeTitle(request.noticeTitle());
        notice.setNoticeType(request.noticeType());
        notice.setNoticeContent(request.noticeContent());
        notice.setStatus(request.status() != null ? request.status() : 0);
        notice.setRemark(request.remark());
        adminNoticeMapper.insert(notice);
    }

    @Override
    @Transactional
    public void update(Long id, AdminNoticeRequest request) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("NOTICE_NOT_FOUND", "通知公告不存在");
        }

        notice.setNoticeTitle(request.noticeTitle());
        notice.setNoticeType(request.noticeType());
        notice.setNoticeContent(request.noticeContent());
        notice.setStatus(request.status());
        notice.setRemark(request.remark());
        adminNoticeMapper.updateById(notice);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("NOTICE_NOT_FOUND", "通知公告不存在");
        }
        adminNoticeMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void markAsRead(Long noticeId, Long userId) {
        AdminNotice notice = adminNoticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BusinessException("NOTICE_NOT_FOUND", "通知公告不存在");
        }

        Long exists = adminNoticeReadMapper.selectCount(
                new LambdaQueryWrapper<AdminNoticeRead>()
                        .eq(AdminNoticeRead::getNoticeId, noticeId)
                        .eq(AdminNoticeRead::getUserId, userId)
        );
        if (exists > 0) {
            return;
        }

        AdminNoticeRead readRecord = new AdminNoticeRead();
        readRecord.setNoticeId(noticeId);
        readRecord.setUserId(userId);
        readRecord.setReadTime(LocalDateTime.now());
        adminNoticeReadMapper.insert(readRecord);
    }

    @Override
    public List<AdminNoticeResponse> unreadList(Long userId) {
        List<AdminNoticeRead> readRecords = adminNoticeReadMapper.selectList(
                new LambdaQueryWrapper<AdminNoticeRead>().eq(AdminNoticeRead::getUserId, userId)
        );
        List<Long> readNoticeIds = readRecords.stream().map(AdminNoticeRead::getNoticeId).toList();

        LambdaQueryWrapper<AdminNotice> wrapper = new LambdaQueryWrapper<AdminNotice>()
                .eq(AdminNotice::getStatus, 0)
                .orderByDesc(AdminNotice::getCreatedAt);

        if (!readNoticeIds.isEmpty()) {
            wrapper.notIn(AdminNotice::getId, readNoticeIds);
        }

        return adminNoticeMapper.selectList(wrapper).stream()
                .map(n -> toResponse(n, false))
                .toList();
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("NOTICE_NOT_FOUND", "通知公告不存在");
        }
        notice.setStatus(status);
        adminNoticeMapper.updateById(notice);
    }

    /**
     * 一键推送公告到全站用户消息中心。
     * Feign 调用为慢速外部 IO，刻意不加 @Transactional（禁止事务内同步外部调用）；
     * 广播本身由 mall-notification 原子性地"先枚举后写入"，本侧失败不会产生半推送状态。
     */
    @Override
    public void pushToAllUsers(Long id) {
        AdminNotice notice = adminNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("NOTICE_NOT_FOUND", "通知公告不存在");
        }
        if (notice.getStatus() == null || notice.getStatus() != 1) {
            throw new BusinessException("NOTICE_NOT_PUBLISHED", "公告未启用，请先启用后再推送");
        }
        if (notice.getNoticeContent() == null || notice.getNoticeContent().isBlank()) {
            throw new BusinessException("NOTICE_CONTENT_EMPTY", "公告内容为空，无法推送");
        }
        notificationFeignClient.broadcastNotification(
                new BroadcastNotificationRequest(
                        PUSH_NOTIFICATION_TYPE, notice.getNoticeTitle(), notice.getNoticeContent()));
    }

    private AdminNoticeResponse toResponse(AdminNotice notice, Boolean isRead) {
        return new AdminNoticeResponse(
                notice.getId(),
                notice.getNoticeTitle(),
                notice.getNoticeType(),
                notice.getNoticeContent(),
                notice.getStatus(),
                notice.getRemark(),
                notice.getCreatedAt(),
                null,
                isRead
        );
    }
}
