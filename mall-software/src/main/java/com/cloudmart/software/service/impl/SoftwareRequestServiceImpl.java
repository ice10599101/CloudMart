package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareRequestCreateInput;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRequest;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRequestMapper;
import com.cloudmart.software.service.SoftwareRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UX06 实现：独立轻表（无奖励）；审核流（PENDING→APPROVED/REJECTED）；
 * 前台状态显式机（OPEN/ANSWERED/CLOSED）；已找到目标可见性实时校验。
 */
@Service
@RequiredArgsConstructor
public class SoftwareRequestServiceImpl implements SoftwareRequestService {

    private static final Set<String> PLATFORMS = Set.of("WINDOWS", "MACOS", "LINUX", "WEB");
    private static final Set<String> LICENSES = Set.of(
            "FREE", "OPENSOURCE", "COMMERCIAL", "TRIAL", "FREEMIUM", "SUBSCRIPTION", "ANY");

    private final SoftwareRequestMapper requestMapper;
    private final SoftwareMapper softwareMapper;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;
    private final Clock clock;

    @Override
    @Transactional
    public Long create(Long userId, SoftwareRequestCreateInput input) {
        if (input == null) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "请求体不能为空");
        }
        String title = input.getTitle() == null ? "" : input.getTitle().trim();
        if (title.isEmpty() || title.length() > 80) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "标题须为 1~80 字");
        }
        String usage = input.getUsageDesc() == null ? "" : input.getUsageDesc().trim();
        if (usage.length() < 20 || usage.length() > 500) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "用途描述须为 20~500 字（说清楚要完成什么）");
        }
        List<String> features = input.getMustHaveFeatures() == null
                ? List.of() : input.getMustHaveFeatures();
        if (features.size() > 10) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "必须功能最多 10 项");
        }
        String platformScope = csvOf(input.getPlatformScope(), PLATFORMS, "平台");
        String license = csvOf(input.getAcceptableLicense(), LICENSES, "许可");
        String keyword = input.getSourceKeyword();
        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "来源关键词最长 100 字");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SoftwareRequest request = new SoftwareRequest();
        request.setUserId(userId);
        request.setTitle(title);
        request.setUsageDesc(usage);
        request.setMustHaveFeatures(features.isEmpty() ? null : String.join(",", features));
        request.setPlatformScope(platformScope);
        request.setAcceptableLicense(license);
        request.setSourceKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
        request.setStatus("OPEN");
        // 提交进入审核（恶意/重复需求可审核；APPROVED 前不公开）
        request.setAuditStatus("PENDING");
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        requestMapper.insert(request);
        return request.getId();
    }

    private String csvOf(List<String> values, Set<String> allowed, String label) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String value : values) {
            if (!allowed.contains(value)) {
                throw new BusinessException("SOFTWARE_REQUEST_INVALID",
                        label + "取值非法：" + value);
            }
        }
        return String.join(",", values);
    }

    @Override
    public List<Map<String, Object>> publicFeed() {
        List<SoftwareRequest> rows = requestMapper.selectList(
                new LambdaQueryWrapper<SoftwareRequest>()
                        .eq(SoftwareRequest::getAuditStatus, "APPROVED")
                        .in(SoftwareRequest::getStatus, "OPEN", "ANSWERED")
                        .isNull(SoftwareRequest::getDeletedAt)
                        .orderByDesc(SoftwareRequest::getCreatedAt)
                        .last("LIMIT 3")); // ≤3 条真实内容；无内容返回空不造假活跃
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareRequest row : rows) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", String.valueOf(row.getId()));
            view.put("title", row.getTitle());
            view.put("status", row.getStatus());
            if ("ANSWERED".equals(row.getStatus()) && row.getMatchedSoftwareId() != null) {
                // 已找到的软件下架后：标注不可用并解释，不给有效获取承诺
                view.put("matchedSoftware", visibleSoftwareView(row.getMatchedSoftwareId()));
            }
            result.add(view);
        }
        return result;
    }

    /** 可见软件摘要；不可见返回 {visible:false, name:null}（解释性，不泄露详情） */
    private Map<String, Object> visibleSoftwareView(Long softwareId) {
        Software software = softwareMapper.selectById(softwareId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("softwareId", String.valueOf(softwareId));
        boolean visible = software != null
                && software.getPublishedRevisionId() != null
                && software.getLifecycle() == SoftwareLifecycle.ACTIVE;
        view.put("visible", visible);
        if (visible) {
            view.put("name", softwareName(software));
        }
        return view;
    }

    private String softwareName(Software software) {
        if (software.getPublishedRevisionId() == null) {
            return null;
        }
        var revision = revisionMapper.selectById(software.getPublishedRevisionId());
        return revision == null ? null : revision.getName();
    }

    @Override
    public List<Map<String, Object>> mine(Long userId) {
        List<SoftwareRequest> rows = requestMapper.selectList(
                new LambdaQueryWrapper<SoftwareRequest>()
                        .eq(SoftwareRequest::getUserId, userId)
                        .isNull(SoftwareRequest::getDeletedAt)
                        .orderByDesc(SoftwareRequest::getCreatedAt)
                        .last("LIMIT 50"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareRequest row : rows) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", String.valueOf(row.getId()));
            view.put("title", row.getTitle());
            view.put("usageDesc", row.getUsageDesc());
            view.put("status", row.getStatus());
            view.put("auditStatus", row.getAuditStatus());
            view.put("auditReason", row.getAuditReason()); // REJECTED 原因本人可见
            view.put("createdAt", row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
            if (row.getMatchedSoftwareId() != null) {
                view.put("matchedSoftware", visibleSoftwareView(row.getMatchedSoftwareId()));
                view.put("matchedReason", row.getMatchedReason());
            }
            result.add(view);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean close(Long userId, Long requestId) {
        SoftwareRequest request = requestMapper.selectById(requestId);
        if (request == null || request.getDeletedAt() != null
                || !request.getUserId().equals(userId)) {
            throw new BusinessException("SOFTWARE_REQUEST_NOT_FOUND", "需求不存在");
        }
        if ("CLOSED".equals(request.getStatus())) {
            return true; // 幂等
        }
        request.setStatus("CLOSED");
        request.setUpdatedAt(LocalDateTime.now(clock));
        requestMapper.updateById(request);
        return true;
    }

    @Override
    public Map<String, Object> adminPage(String auditStatus, String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        LambdaQueryWrapper<SoftwareRequest> wrapper = new LambdaQueryWrapper<SoftwareRequest>()
                .isNull(SoftwareRequest::getDeletedAt)
                .orderByAsc(SoftwareRequest::getAuditStatus)
                .orderByDesc(SoftwareRequest::getCreatedAt);
        if (auditStatus != null && !auditStatus.isBlank()) {
            wrapper.eq(SoftwareRequest::getAuditStatus, auditStatus.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SoftwareRequest::getStatus, status.trim());
        }
        Page<SoftwareRequest> p = requestMapper.selectPage(Page.of(safePage, safeSize), wrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SoftwareRequest row : p.getRecords()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", String.valueOf(row.getId()));
            view.put("userId", String.valueOf(row.getUserId()));
            view.put("title", row.getTitle());
            view.put("usageDesc", row.getUsageDesc());
            view.put("mustHaveFeatures", row.getMustHaveFeatures());
            view.put("platformScope", row.getPlatformScope());
            view.put("acceptableLicense", row.getAcceptableLicense());
            view.put("sourceKeyword", row.getSourceKeyword());
            view.put("status", row.getStatus());
            view.put("auditStatus", row.getAuditStatus());
            view.put("auditReason", row.getAuditReason());
            if (row.getMatchedSoftwareId() != null) {
                view.put("matchedSoftwareId", String.valueOf(row.getMatchedSoftwareId()));
                view.put("matchedReason", row.getMatchedReason());
                view.put("matchedVisible", visibleSoftwareView(row.getMatchedSoftwareId()).get("visible"));
            }
            view.put("createdAt", row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
            rows.add(view);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", p.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    @Override
    @Transactional
    public boolean audit(Long adminUserId, Long requestId, boolean approved,
                         String reason, String idempotencyKey) {
        SoftwareRequest request = requestMapper.selectById(requestId);
        if (request == null || request.getDeletedAt() != null) {
            throw new BusinessException("SOFTWARE_REQUEST_NOT_FOUND", "需求不存在");
        }
        if (!approved && (reason == null || reason.isBlank())) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "拒绝必须说明原因");
        }
        request.setAuditStatus(approved ? "APPROVED" : "REJECTED");
        request.setAuditReason(reason == null || reason.isBlank() ? null : reason.trim());
        request.setUpdatedAt(LocalDateTime.now(clock));
        requestMapper.updateById(request);
        return true;
    }

    @Override
    @Transactional
    public boolean markMatched(Long adminUserId, Long requestId, Long softwareId,
                               String reason, String idempotencyKey) {
        SoftwareRequest request = requestMapper.selectById(requestId);
        if (request == null || request.getDeletedAt() != null
                || !"APPROVED".equals(request.getAuditStatus())) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "需求不存在或未过审");
        }
        if (softwareId == null) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "必须指定已找到软件");
        }
        // 候选必须为可见软件（不让投稿者自己刷"解决数"；已找到=真实可见目标）
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getPublishedRevisionId() == null
                || software.getLifecycle() != SoftwareLifecycle.ACTIVE) {
            throw new BusinessException("SOFTWARE_REQUEST_INVALID", "已找到软件必须为当前可见软件");
        }
        request.setMatchedSoftwareId(softwareId);
        request.setMatchedReason(reason == null || reason.isBlank() ? null : reason.trim());
        request.setMatchedAt(LocalDateTime.now(clock));
        request.setMatchedBy(adminUserId);
        request.setStatus("ANSWERED");
        request.setUpdatedAt(LocalDateTime.now(clock));
        requestMapper.updateById(request);
        return true;
    }
}
