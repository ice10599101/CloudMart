package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminGuideSaveRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareGuide;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareGuideMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.service.SoftwareGuideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UX04-A 实现：教程元数据 CRUD + 公开读取（实时校验文章公开状态）。
 * 公开谓词 = enabled 且未删除 且 社区文章可读（Feign 批量校验，失败降级隐藏并 WARN——
 * 宁可少展示不展示"文章已撤回但软件入口还在"的失效教程）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareGuideServiceImpl implements SoftwareGuideService {

    private static final Set<String> TYPES = Set.of(
            "INSTALL_GUIDE", "WORKFLOW", "COMPARISON", "TROUBLESHOOTING");
    private static final Set<String> PLATFORMS = Set.of("WINDOWS", "MACOS", "LINUX", "WEB");
    private static final int MAX_RELATED = 3;
    private static final int MAX_MAINTENANCE_SCAN = 500;

    private final SoftwareGuideMapper guideMapper;
    private final SoftwareMapper softwareMapper;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;
    private final CommunitySupportFeignClient communitySupportFeignClient;
    private final Clock clock;

    @Override
    public Map<String, Object> publicGuides(String guideType, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        LambdaQueryWrapper<SoftwareGuide> wrapper = new LambdaQueryWrapper<SoftwareGuide>()
                .eq(SoftwareGuide::getEnabled, true)
                .isNull(SoftwareGuide::getDeletedAt)
                .orderByAsc(SoftwareGuide::getDisplayOrder)
                .orderByDesc(SoftwareGuide::getId);
        if (guideType != null && !guideType.isBlank()) {
            wrapper.eq(SoftwareGuide::getGuideType, guideType.trim());
        }
        Page<SoftwareGuide> p = guideMapper.selectPage(Page.of(safePage, safeSize), wrapper);
        List<Long> articleIds = p.getRecords().stream()
                .map(SoftwareGuide::getArticleId).distinct().toList();
        Map<Long, Map<String, Object>> articleById = articleStatus(articleIds);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SoftwareGuide guide : p.getRecords()) {
            Map<String, Object> article = articleById.get(guide.getArticleId());
            if (article == null || !Boolean.TRUE.equals(article.get("public"))) {
                continue; // 文章撤回/非公开：立即从软件入口隐藏
            }
            rows.add(publicView(guide, article));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", p.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    @Override
    public Map<String, Object> publicGuideDetail(Long guideId) {
        SoftwareGuide guide = guideMapper.selectById(guideId);
        if (guide == null || guide.getDeletedAt() != null
                || !Boolean.TRUE.equals(guide.getEnabled())) {
            throw new BusinessException("SOFTWARE_GUIDE_NOT_FOUND", "教程不存在");
        }
        Map<Long, Map<String, Object>> articleById =
                articleStatus(List.of(guide.getArticleId()));
        Map<String, Object> article = articleById.get(guide.getArticleId());
        if (article == null || !Boolean.TRUE.equals(article.get("public"))) {
            // 文章撤回：404 语义（不泄露存在性）
            throw new BusinessException("SOFTWARE_GUIDE_NOT_FOUND", "教程不存在");
        }
        Map<String, Object> view = publicView(guide, article);
        view.put("appliesToVersion", guide.getAppliesToVersion());
        view.put("checkedAt", guide.getCheckedAt() == null ? null : guide.getCheckedAt().toString());
        view.put("checkNote", guide.getCheckNote());
        // 关联软件：实时过滤不可见（封禁/下架不泄露，不给失效获取按钮）
        List<Map<String, Object>> related = new ArrayList<>();
        for (Long softwareId : parseSoftwareIds(guide.getRelatedSoftwareIds())) {
            Software software = softwareMapper.selectById(softwareId);
            if (software != null && software.getPublishedRevisionId() != null
                    && software.getLifecycle() == com.cloudmart.software.enums.SoftwareLifecycle.ACTIVE) {
                com.cloudmart.software.entity.SoftwareRevision revision =
                        revisionMapper.selectById(software.getPublishedRevisionId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("softwareId", String.valueOf(softwareId));
                row.put("name", revision == null ? null : revision.getName());
                related.add(row);
            }
        }
        view.put("relatedSoftwares", related);
        view.put("relatedTopicId", guide.getRelatedTopicId() == null
                ? null : String.valueOf(guide.getRelatedTopicId()));
        return view;
    }

    @Override
    public Map<String, Object> adminGuides(String guideType, boolean maintenanceOnly,
                                           int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        LambdaQueryWrapper<SoftwareGuide> wrapper = new LambdaQueryWrapper<SoftwareGuide>()
                .isNull(SoftwareGuide::getDeletedAt)
                .orderByAsc(SoftwareGuide::getDisplayOrder)
                .orderByDesc(SoftwareGuide::getId);
        if (guideType != null && !guideType.isBlank()) {
            wrapper.eq(SoftwareGuide::getGuideType, guideType.trim());
        }
        List<Map<String, Object>> rows;
        long total;
        if (maintenanceOnly) {
            // 维护队列需跨页筛选；教程由运营人工维护、规模很小，扫描上限防止误用。
            List<SoftwareGuide> all = guideMapper.selectList(
                    wrapper.last("LIMIT " + (MAX_MAINTENANCE_SCAN + 1)));
            if (all.size() > MAX_MAINTENANCE_SCAN) {
                log.warn("教程维护队列扫描超过上限 {}，结果可能不完整", MAX_MAINTENANCE_SCAN);
                all = all.subList(0, MAX_MAINTENANCE_SCAN);
            }
            rows = adminRows(all).stream()
                    .filter(row -> Boolean.TRUE.equals(row.get("maintenanceRequired")))
                    .toList();
            total = rows.size();
            int from = Math.min((safePage - 1) * safeSize, rows.size());
            int to = Math.min(from + safeSize, rows.size());
            rows = rows.subList(from, to);
        } else {
            Page<SoftwareGuide> p = guideMapper.selectPage(Page.of(safePage, safeSize), wrapper);
            rows = adminRows(p.getRecords());
            total = p.getTotal();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private List<Map<String, Object>> adminRows(List<SoftwareGuide> guides) {
        List<Long> articleIds = guides.stream()
                .map(SoftwareGuide::getArticleId).distinct().toList();
        Map<Long, Map<String, Object>> articleById = articleStatus(articleIds);
        List<Long> relatedIds = guides.stream()
                .flatMap(g -> parseSoftwareIds(g.getRelatedSoftwareIds()).stream())
                .distinct().toList();
        Map<Long, Software> softwareById = relatedIds.isEmpty() ? Map.of()
                : softwareMapper.selectBatchIds(relatedIds).stream()
                    .collect(java.util.stream.Collectors.toMap(Software::getId, s -> s));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SoftwareGuide guide : guides) {
            Map<String, Object> row = adminView(guide);
            Map<String, Object> article = articleById.get(guide.getArticleId());
            row.put("articlePublic", article != null && Boolean.TRUE.equals(article.get("public")));
            row.put("articleTitle", article == null ? null : article.get("title"));
            // UX04：关联软件被封禁/下架/删除后进入后台维护队列；公开页已隐藏该软件，
            // 运营需要看到“哪篇教程的哪个关联项已失效”，而不是等用户反馈。
            List<Map<String, Object>> issues = new ArrayList<>();
            for (Long softwareId : parseSoftwareIds(guide.getRelatedSoftwareIds())) {
                Software software = softwareById.get(softwareId);
                String reason = maintenanceReason(software);
                if (reason != null) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("softwareId", String.valueOf(softwareId));
                    issue.put("reason", reason);
                    issues.add(issue);
                }
            }
            row.put("maintenanceRequired", !issues.isEmpty());
            row.put("maintenanceIssues", issues);
            rows.add(row);
        }
        return rows;
    }

    @Override
    @Transactional
    public Long saveGuide(Long guideId, AdminGuideSaveRequest request, String idempotencyKey) {
        if (request == null) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "请求体不能为空");
        }
        String type = request.getGuideType() == null ? "" : request.getGuideType().trim();
        if (!TYPES.contains(type)) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID",
                    "guideType 须为 INSTALL_GUIDE/WORKFLOW/COMPARISON/TROUBLESHOOTING");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getTitle().length() > 120) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "标题须为 1~120 字");
        }
        if (request.getSummary() != null && request.getSummary().length() > 200) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "摘要最长 200 字");
        }
        if (request.getArticleId() == null) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "必须关联社区文章（正文与审核真源）");
        }
        List<Long> related = request.getRelatedSoftwareIds() == null ? List.of()
                : request.getRelatedSoftwareIds();
        if (related.size() > MAX_RELATED) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID",
                    "关联软件最多 " + MAX_RELATED + " 个");
        }
        if (related.isEmpty() && request.getRelatedTopicId() == null) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID",
                    "教程必须至少关联一个软件或专题");
        }
        for (Long softwareId : related) {
            Software software = softwareMapper.selectById(softwareId);
            if (software == null || software.getPublishedRevisionId() == null) {
                throw new BusinessException("SOFTWARE_GUIDE_INVALID",
                        "关联软件不存在或未发布（softwareId=" + softwareId + "）");
            }
        }
        for (String p : request.getPlatformScope() == null ? List.<String>of() : request.getPlatformScope()) {
            if (!PLATFORMS.contains(p)) {
                throw new BusinessException("SOFTWARE_GUIDE_INVALID",
                        "适用平台仅允许 WINDOWS/MACOS/LINUX/WEB");
            }
        }
        if (request.getCheckNote() != null && request.getCheckNote().length() > 200) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "检查说明最长 200 字");
        }

        // articleId 唯一 + 公开状态预检（保存时提示，读取时仍实时校验）
        SoftwareGuide dup = guideMapper.selectOne(new LambdaQueryWrapper<SoftwareGuide>()
                .eq(SoftwareGuide::getArticleId, request.getArticleId())
                .isNull(SoftwareGuide::getDeletedAt)
                .last("LIMIT 1"));
        if (dup != null && (guideId == null || !dup.getId().equals(guideId))) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "该社区文章已关联其他教程");
        }
        Map<Long, Map<String, Object>> articleById = articleStatus(List.of(request.getArticleId()));
        Map<String, Object> article = articleById.get(request.getArticleId());
        if (article == null || !Boolean.TRUE.equals(article.get("public"))) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID",
                    "关联文章当前非公开（须已发布且审核通过），请先在社区完成发布与审核");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SoftwareGuide guide = guideId == null ? new SoftwareGuide()
                : guideMapper.selectById(guideId);
        if (guideId != null && (guide == null || guide.getDeletedAt() != null)) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "教程不存在");
        }
        if (guideId == null) {
            guide.setCreatedAt(now);
        }
        guide.setGuideType(type);
        guide.setTitle(request.getTitle().trim());
        guide.setSummary(trimToNull(request.getSummary(), 200));
        guide.setArticleId(request.getArticleId());
        guide.setCoverAssetId(request.getCoverAssetId());
        guide.setRelatedSoftwareIds(related.isEmpty() ? null
                : toJson(related));
        guide.setRelatedTopicId(request.getRelatedTopicId());
        guide.setPlatformScope(request.getPlatformScope() == null || request.getPlatformScope().isEmpty()
                ? null : String.join(",", request.getPlatformScope()));
        guide.setAppliesToVersion(trimToNull(request.getAppliesToVersion(), 60));
        guide.setCheckedAt(request.getCheckedAt());
        guide.setCheckNote(trimToNull(request.getCheckNote(), 200));
        guide.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        guide.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        guide.setUpdatedAt(now);
        if (guideId == null) {
            guideMapper.insert(guide);
        } else {
            guideMapper.updateById(guide);
        }
        return guide.getId();
    }

    @Override
    @Transactional
    public boolean toggle(Long guideId, boolean enabled, String idempotencyKey) {
        SoftwareGuide guide = guideMapper.selectById(guideId);
        if (guide == null || guide.getDeletedAt() != null) {
            throw new BusinessException("SOFTWARE_GUIDE_INVALID", "教程不存在");
        }
        guide.setEnabled(enabled);
        guide.setUpdatedAt(LocalDateTime.now(clock));
        guideMapper.updateById(guide);
        return true;
    }

    @Override
    @Transactional
    public boolean delete(Long guideId, String idempotencyKey) {
        SoftwareGuide guide = guideMapper.selectById(guideId);
        if (guide == null || guide.getDeletedAt() != null) {
            return true;
        }
        guide.setDeletedAt(LocalDateTime.now(clock));
        guide.setEnabled(false);
        guide.setUpdatedAt(LocalDateTime.now(clock));
        guideMapper.updateById(guide);
        return true;
    }

    // ------------------------------------------------------------------

    /** 批量文章公开状态（Feign；失败降级为全部非公开——教程宁缺勿假） */
    private Map<Long, Map<String, Object>> articleStatus(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> rows =
                    communitySupportFeignClient.postPublicStatus(articleIds).data();
            Map<Long, Map<String, Object>> byId = new HashMap<>();
            for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
                long id = Long.parseLong(String.valueOf(row.get("postId")));
                byId.put(id, row);
            }
            return byId;
        } catch (Exception e) {
            log.warn("教程文章状态查询失败（按全部非公开处理）: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 维护队列判定：关联软件被删除/未发布/非 ACTIVE 时返回原因，健康返回 null。 */
    private static String maintenanceReason(Software software) {
        if (software == null) {
            return "NOT_FOUND";
        }
        if (software.getPublishedRevisionId() == null) {
            return "NOT_PUBLISHED";
        }
        if (software.getLifecycle() != com.cloudmart.software.enums.SoftwareLifecycle.ACTIVE) {
            return "NOT_ACTIVE";
        }
        return null;
    }

    private Map<String, Object> publicView(SoftwareGuide guide, Map<String, Object> article) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(guide.getId()));
        view.put("guideType", guide.getGuideType());
        view.put("title", guide.getTitle());
        view.put("summary", guide.getSummary());
        view.put("coverAssetId", guide.getCoverAssetId() == null
                ? article.get("coverImage") : String.valueOf(guide.getCoverAssetId()));
        view.put("articleId", String.valueOf(guide.getArticleId()));
        view.put("platformScope", guide.getPlatformScope() == null ? List.of()
                : List.of(guide.getPlatformScope().split(",")));
        return view;
    }

    private Map<String, Object> adminView(SoftwareGuide guide) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(guide.getId()));
        view.put("guideType", guide.getGuideType());
        view.put("title", guide.getTitle());
        view.put("summary", guide.getSummary());
        view.put("articleId", String.valueOf(guide.getArticleId()));
        view.put("coverAssetId", guide.getCoverAssetId() == null
                ? null : String.valueOf(guide.getCoverAssetId()));
        view.put("relatedSoftwareIds", parseSoftwareIds(guide.getRelatedSoftwareIds())
                .stream().map(String::valueOf).toList());
        view.put("relatedTopicId", guide.getRelatedTopicId() == null
                ? null : String.valueOf(guide.getRelatedTopicId()));
        view.put("platformScope", guide.getPlatformScope() == null ? List.of()
                : List.of(guide.getPlatformScope().split(",")));
        view.put("appliesToVersion", guide.getAppliesToVersion());
        view.put("checkedAt", guide.getCheckedAt() == null ? null : guide.getCheckedAt().toString());
        view.put("checkNote", guide.getCheckNote());
        view.put("enabled", Boolean.TRUE.equals(guide.getEnabled()));
        view.put("displayOrder", guide.getDisplayOrder());
        return view;
    }

    private List<Long> parseSoftwareIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(
                    json, new tools.jackson.core.type.TypeReference<List<Long>>() { });
        } catch (Exception e) {
            log.warn("关联软件解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String toJson(List<Long> ids) {
        try {
            return new tools.jackson.databind.ObjectMapper().writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException("关联软件序列化失败", e);
        }
    }

    private String trimToNull(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
