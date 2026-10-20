package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminHomeSectionSaveRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareHomeSection;
import com.cloudmart.software.entity.SoftwareHomeSectionItem;
import com.cloudmart.software.entity.SoftwareTopic;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareHomeSectionItemMapper;
import com.cloudmart.software.repository.SoftwareHomeSectionMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareTopicMapper;
import com.cloudmart.software.service.SoftwareHomeSectionService;
import com.cloudmart.software.service.SoftwareQueryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.SoftwareListQuery;
import com.cloudmart.software.vo.software.SoftwareCardVO;
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
 * 首页内容运营配置（UX01）实现。
 * 公共聚合：enabled + 投放期内 + 平台匹配 → 按 data_source 聚合 →
 * 实时过滤不可见目标（复用详情同语义）；单块聚合失败记日志返回空，不拖垮其他块。
 * 管理侧：kind/data_source 白名单、slot_key 唯一、时间 start<end、MANUAL 条目同事务替换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareHomeSectionServiceImpl implements SoftwareHomeSectionService {

    private static final Set<String> KINDS = Set.of(
            "SPOTLIGHT", "SCENARIOS", "SOFTWARE_GRID", "RANKING_LIST", "UPDATE_FEED", "GUIDE_LIST");
    private static final Set<String> SOURCES = Set.of(
            "MANUAL", "FEATURED", "LATEST", "RANKING", "TOPIC", "GUIDE");
    private static final Set<String> TARGET_TYPES = Set.of("SOFTWARE", "TOPIC", "GUIDE", "FILTER");
    private static final Set<String> PLATFORMS = Set.of("WEB", "APP", "TARO");
    private static final int MAX_LIMIT = 20;

    private final SoftwareHomeSectionMapper sectionMapper;
    private final SoftwareHomeSectionItemMapper itemMapper;
    private final SoftwareMapper softwareMapper;
    private final SoftwareTopicMapper topicMapper;
    private final com.cloudmart.software.repository.SoftwareGuideMapper guideMapper;
    private final CommunitySupportFeignClient communitySupportFeignClient;
    private final SoftwareQueryService softwareQueryService;
    private final Clock clock;

    // ------------------------------------------------------------------
    // 公共聚合
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> publicHome(String platform) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SoftwareHomeSection> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<SoftwareHomeSection>()
                        .eq(SoftwareHomeSection::getEnabled, true)
                        .isNull(SoftwareHomeSection::getDeletedAt)
                        .orderByAsc(SoftwareHomeSection::getDisplayOrder)
                        .orderByAsc(SoftwareHomeSection::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareHomeSection section : sections) {
            if (!inWindow(section, now) || !platformMatches(section, platform)) {
                continue;
            }
            try {
                Map<String, Object> block = aggregate(section, now);
                if (block != null) {
                    result.add(block);
                }
            } catch (Exception e) {
                // 单块失败协议：本块 ERROR 显式标记（前端显示本区重试），其余区块正常
                log.warn("首页区块聚合失败 sectionId={} slot={}: {}",
                        section.getId(), section.getSlotKey(), e.getMessage());
                Map<String, Object> errorBlock = baseBlock(section);
                errorBlock.put("state", "ERROR");
                errorBlock.put("items", List.of());
                result.add(errorBlock);
            }
        }
        return result;
    }

    private Map<String, Object> baseBlock(SoftwareHomeSection section) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", String.valueOf(section.getId()));
        block.put("slotKey", section.getSlotKey());
        block.put("kind", section.getKind());
        block.put("title", section.getTitle());
        block.put("subtitle", section.getSubtitle());
        block.put("moreTarget", moreTarget(section));
        return block;
    }

    private Map<String, Object> aggregate(SoftwareHomeSection section, LocalDateTime now) {
        int limit = safeLimit(section.getLimitCount());
        List<Map<String, Object>> items = new ArrayList<>();
        switch (section.getDataSource() == null ? "" : section.getDataSource()) {
            case "MANUAL" -> items = manualItems(section, limit);
            case "FEATURED" -> items = softwareCards("featured", limit);
            case "LATEST" -> items = softwareCards("latest", limit);
            case "RANKING" -> items = softwareCards("popular", limit);
            case "TOPIC" -> items = topicItems(limit);
            case "GUIDE" -> items = guideItems(limit);
            default -> items = List.of();
        }
        Map<String, Object> block = baseBlock(section);
        int min = section.getMinItems() == null ? 0 : section.getMinItems();
        if (items.size() < min) {
            // 内容不足：HIDE（不返回区块）或 EMPTY（显式空态）
            if ("EMPTY".equals(section.getFallbackPolicy())) {
                block.put("state", "EMPTY");
                block.put("items", List.of());
                block.put("hasMore", false);
                return block;
            }
            return null;
        }
        block.put("state", "OK");
        block.put("items", items);
        block.put("hasMore", false);
        return block;
    }

    /** MANUAL 条目：实时过滤不可见目标（下架/封禁不泄露），软删除主题剔除 */
    private List<Map<String, Object>> manualItems(SoftwareHomeSection section, int limit) {
        List<SoftwareHomeSectionItem> rows = itemMapper.selectList(
                new LambdaQueryWrapper<SoftwareHomeSectionItem>()
                        .eq(SoftwareHomeSectionItem::getSectionId, section.getId())
                        .orderByAsc(SoftwareHomeSectionItem::getDisplayOrder)
                        .orderByAsc(SoftwareHomeSectionItem::getId));
        // 教程条目批量预载 + 文章公开状态（文章撤回立即从首页入口隐藏）
        List<Long> guideIds = rows.stream()
                .filter(row -> "GUIDE".equals(row.getTargetType()) && row.getTargetId() != null)
                .map(SoftwareHomeSectionItem::getTargetId).distinct().toList();
        Map<Long, com.cloudmart.software.entity.SoftwareGuide> guideById = guideIds.isEmpty()
                ? Map.of()
                : guideMapper.selectBatchIds(guideIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.cloudmart.software.entity.SoftwareGuide::getId, g -> g));
        Map<Long, Boolean> articlePublic = articlePublic(guideById.values().stream()
                .map(com.cloudmart.software.entity.SoftwareGuide::getArticleId).toList());
        List<Map<String, Object>> items = new ArrayList<>();
        for (SoftwareHomeSectionItem row : rows) {
            if (items.size() >= limit) {
                break;
            }
            Map<String, Object> item = baseItem(row);
            switch (row.getTargetType() == null ? "" : row.getTargetType()) {
                case "SOFTWARE" -> {
                    if (row.getTargetId() == null) {
                        continue;
                    }
                    Software software = softwareMapper.selectById(row.getTargetId());
                    if (software == null || software.getPublishedRevisionId() == null
                            || software.getLifecycle() != com.cloudmart.software.enums.SoftwareLifecycle.ACTIVE) {
                        continue;
                    }
                    // 复用卡片查询（可见性已含发布校验）
                    List<SoftwareCardVO> card = softwareQueryService.cardsByIds(List.of(row.getTargetId()));
                    if (card.isEmpty()) {
                        continue;
                    }
                    item.put("software", card.get(0));
                }
                case "TOPIC" -> {
                    if (row.getTargetId() == null) {
                        continue;
                    }
                    SoftwareTopic topic = topicMapper.selectById(row.getTargetId());
                    if (topic == null || !"PUBLISHED".equals(topic.getStatus())) {
                        continue;
                    }
                    item.put("topic", Map.of(
                            "id", String.valueOf(topic.getId()),
                            "slug", topic.getSlug() == null ? "" : topic.getSlug(),
                            "title", topic.getTitle() == null ? "" : topic.getTitle()));
                }
                case "GUIDE" -> {
                    // UX04 教程条目：enabled+文章公开（文章撤回立即从首页入口隐藏）
                    if (row.getTargetId() == null) {
                        continue;
                    }
                    com.cloudmart.software.entity.SoftwareGuide guide =
                            guideById.get(row.getTargetId());
                    if (guide == null || guide.getDeletedAt() != null
                            || !Boolean.TRUE.equals(guide.getEnabled())
                            || !Boolean.TRUE.equals(articlePublic.get(guide.getArticleId()))) {
                        continue;
                    }
                    item.put("guide", java.util.Map.of(
                            "id", String.valueOf(guide.getId()),
                            "title", guide.getTitle()));
                }
                case "FILTER" -> item.put("filterPreset", row.getFilterPreset());
                default -> { }
            }
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> softwareCards(String sort, int limit) {
        SoftwareListQuery query = new SoftwareListQuery();
        query.setSort(sort);
        query.setPage(1);
        query.setSize(limit);
        Page<SoftwareCardVO> page = softwareQueryService.pageCards(query);
        List<Map<String, Object>> items = new ArrayList<>();
        for (SoftwareCardVO card : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetType", "SOFTWARE");
            item.put("software", card);
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> topicItems(int limit) {
        List<SoftwareTopic> topics = topicMapper.selectList(
                new LambdaQueryWrapper<SoftwareTopic>()
                        .eq(SoftwareTopic::getStatus, "PUBLISHED")
                        .orderByDesc(SoftwareTopic::getUpdatedAt)
                        .last("LIMIT " + limit));
        List<Map<String, Object>> items = new ArrayList<>();
        for (SoftwareTopic topic : topics) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetType", "TOPIC");
            item.put("topic", Map.of(
                    "id", String.valueOf(topic.getId()),
                    "slug", topic.getSlug() == null ? "" : topic.getSlug(),
                    "title", topic.getTitle() == null ? "" : topic.getTitle(),
                    "summary", topic.getSummary() == null ? "" : topic.getSummary()));
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> baseItem(SoftwareHomeSectionItem row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", String.valueOf(row.getId()));
        item.put("targetType", row.getTargetType());
        if (row.getHeadline() != null && !row.getHeadline().isBlank()) {
            item.put("headline", row.getHeadline());
        }
        if (row.getEditorNote() != null && !row.getEditorNote().isBlank()) {
            item.put("editorNote", row.getEditorNote());
        }
        if (row.getCoverAssetId() != null) {
            item.put("coverAssetId", String.valueOf(row.getCoverAssetId()));
        }
        return item;
    }

    /** UX04：教程数据源——enabled+未删除+文章公开（撤回立即隐藏） */
    private List<Map<String, Object>> guideItems(int limit) {
        // 多取候选以抵消文章撤回造成的过滤损耗；教程由运营维护，总量有限。
        List<com.cloudmart.software.entity.SoftwareGuide> guides = guideMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareGuide>()
                        .eq(com.cloudmart.software.entity.SoftwareGuide::getEnabled, true)
                        .isNull(com.cloudmart.software.entity.SoftwareGuide::getDeletedAt)
                        .orderByAsc(com.cloudmart.software.entity.SoftwareGuide::getDisplayOrder)
                        .last("LIMIT " + MAX_LIMIT));
        Map<Long, Boolean> articlePublic = articlePublic(guides.stream()
                .map(com.cloudmart.software.entity.SoftwareGuide::getArticleId).toList());
        List<Map<String, Object>> items = new ArrayList<>();
        for (var guide : guides) {
            if (items.size() >= limit) {
                break;
            }
            if (!Boolean.TRUE.equals(articlePublic.get(guide.getArticleId()))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetType", "GUIDE");
            item.put("guide", java.util.Map.of(
                    "id", String.valueOf(guide.getId()),
                    "title", guide.getTitle(),
                    "summary", guide.getSummary() == null ? "" : guide.getSummary(),
                    "guideType", guide.getGuideType()));
            items.add(item);
        }
        return items;
    }

    /** 社区文章公开状态（批量；失败按全部非公开处理——教程宁缺勿假） */
    private Map<Long, Boolean> articlePublic(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> rows =
                    communitySupportFeignClient.postPublicStatus(articleIds).data();
            Map<Long, Boolean> byId = new HashMap<>();
            for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
                byId.put(Long.parseLong(String.valueOf(row.get("postId"))),
                        Boolean.TRUE.equals(row.get("public")));
            }
            return byId;
        } catch (Exception e) {
            log.warn("首页教程文章状态查询失败（按全部非公开处理）: {}", e.getMessage());
            return Map.of();
        }
    }

    private String moreTarget(SoftwareHomeSection section) {
        return switch (section.getKind()) {
            case "SOFTWARE_GRID" -> "/software";
            case "RANKING_LIST" -> "/software?sort=popular";
            case "UPDATE_FEED" -> "/mine/software";
            default -> null;
        };
    }

    private boolean inWindow(SoftwareHomeSection section, LocalDateTime now) {
        if (section.getStartAt() != null && now.isBefore(section.getStartAt())) {
            return false;
        }
        return section.getEndAt() == null || !now.isAfter(section.getEndAt());
    }

    private boolean platformMatches(SoftwareHomeSection section, String platform) {
        String scope = section.getPlatformScope();
        if (scope == null || scope.isBlank()) {
            return true;
        }
        if (platform == null || platform.isBlank()) {
            return true; // 未带端参数的调用（Web 默认）视为 WEB
        }
        for (String p : scope.split(",")) {
            if (p.trim().equalsIgnoreCase(platform)) {
                return true;
            }
        }
        return false;
    }

    private int safeLimit(Integer limitCount) {
        return limitCount == null || limitCount < 1 ? 8 : Math.min(limitCount, MAX_LIMIT);
    }

    // ------------------------------------------------------------------
    // 管理侧
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> adminSections() {
        List<SoftwareHomeSection> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<SoftwareHomeSection>()
                        .isNull(SoftwareHomeSection::getDeletedAt)
                        .orderByAsc(SoftwareHomeSection::getDisplayOrder)
                        .orderByAsc(SoftwareHomeSection::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareHomeSection section : sections) {
            Map<String, Object> view = adminView(section);
            view.put("items", itemMapper.selectList(
                            new LambdaQueryWrapper<SoftwareHomeSectionItem>()
                                    .eq(SoftwareHomeSectionItem::getSectionId, section.getId())
                                    .orderByAsc(SoftwareHomeSectionItem::getDisplayOrder)
                                    .orderByAsc(SoftwareHomeSectionItem::getId))
                    .stream().map(this::adminItemView).toList());
            result.add(view);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> preview(String platform) {
        // 预览复用聚合逻辑，但读取全部（含未启用/不在投放期）区块
        List<SoftwareHomeSection> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<SoftwareHomeSection>()
                        .isNull(SoftwareHomeSection::getDeletedAt)
                        .orderByAsc(SoftwareHomeSection::getDisplayOrder)
                        .orderByAsc(SoftwareHomeSection::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoftwareHomeSection section : sections) {
            Map<String, Object> view = adminView(section);
            view.put("state", Boolean.TRUE.equals(section.getEnabled())
                    && inWindow(section, LocalDateTime.now(clock))
                    && platformMatches(section, platform) ? "LIVE" : "OFFLINE");
            view.put("items", itemMapper.selectList(
                            new LambdaQueryWrapper<SoftwareHomeSectionItem>()
                                    .eq(SoftwareHomeSectionItem::getSectionId, section.getId())
                                    .orderByAsc(SoftwareHomeSectionItem::getDisplayOrder))
                    .stream().map(this::adminItemView).toList());
            result.add(view);
        }
        return result;
    }

    private Map<String, Object> adminView(SoftwareHomeSection section) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(section.getId()));
        view.put("slotKey", section.getSlotKey());
        view.put("title", section.getTitle());
        view.put("subtitle", section.getSubtitle());
        view.put("kind", section.getKind());
        view.put("enabled", Boolean.TRUE.equals(section.getEnabled()));
        view.put("displayOrder", section.getDisplayOrder());
        view.put("startAt", section.getStartAt() == null ? null : section.getStartAt().toString());
        view.put("endAt", section.getEndAt() == null ? null : section.getEndAt().toString());
        view.put("platformScope", section.getPlatformScope() == null ? List.of()
                : List.of(section.getPlatformScope().split(",")));
        view.put("limitCount", section.getLimitCount());
        view.put("minItems", section.getMinItems());
        view.put("dataSource", section.getDataSource());
        view.put("fallbackPolicy", section.getFallbackPolicy());
        return view;
    }

    private Map<String, Object> adminItemView(SoftwareHomeSectionItem row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(row.getId()));
        view.put("targetType", row.getTargetType());
        view.put("targetId", row.getTargetId() == null ? null : String.valueOf(row.getTargetId()));
        view.put("filterPreset", row.getFilterPreset());
        view.put("headline", row.getHeadline());
        view.put("editorNote", row.getEditorNote());
        view.put("coverAssetId", row.getCoverAssetId() == null ? null : String.valueOf(row.getCoverAssetId()));
        view.put("displayOrder", row.getDisplayOrder());
        return view;
    }

    @Override
    @Transactional
    public Long saveSection(Long sectionId, AdminHomeSectionSaveRequest request, String idempotencyKey) {
        if (request == null) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "请求体不能为空");
        }
        String kind = request.getKind() == null ? "" : request.getKind().trim();
        if (!KINDS.contains(kind)) {
            throw new BusinessException("SOFTWARE_HOME_INVALID",
                    "kind 须为 SPOTLIGHT/SCENARIOS/SOFTWARE_GRID/RANKING_LIST/UPDATE_FEED/GUIDE_LIST");
        }
        String source = request.getDataSource() == null ? "" : request.getDataSource().trim();
        if (!SOURCES.contains(source)) {
            throw new BusinessException("SOFTWARE_HOME_INVALID",
                    "dataSource 须为 MANUAL/FEATURED/LATEST/RANKING/TOPIC/GUIDE");
        }
        // 拒绝任意外部数据源/脚本形态（白名单之外一律不接受）
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getTitle().length() > 60) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "标题须为 1~60 字");
        }
        if (request.getSubtitle() != null && request.getSubtitle().length() > 120) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "副标题最长 120 字");
        }
        if (request.getSlotKey() == null || request.getSlotKey().isBlank()
                || !request.getSlotKey().matches("[a-z0-9-]{2,40}")) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "slotKey 须为 2~40 位小写字母/数字/连字符");
        }
        if (request.getStartAt() != null && request.getEndAt() != null
                && !request.getStartAt().isBefore(request.getEndAt())) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "投放开始须早于结束");
        }
        int limitCount = request.getLimitCount() == null ? 8 : request.getLimitCount();
        if (limitCount < 1 || limitCount > MAX_LIMIT) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "条目上限须为 1~20");
        }
        int minItems = request.getMinItems() == null ? 0 : request.getMinItems();
        if (minItems < 0 || minItems > limitCount) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "最少条目须为 0~上限");
        }
        String fallback = request.getFallbackPolicy() == null ? "HIDE" : request.getFallbackPolicy();
        if (!Set.of("HIDE", "EMPTY").contains(fallback)) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "fallbackPolicy 须为 HIDE/EMPTY");
        }
        List<String> platforms = request.getPlatformScope() == null ? List.of() : request.getPlatformScope();
        for (String p : platforms) {
            if (!PLATFORMS.contains(p)) {
                throw new BusinessException("SOFTWARE_HOME_INVALID", "平台范围仅允许 WEB/APP/TARO");
            }
        }

        // slot_key 唯一（排除自身）
        SoftwareHomeSection dup = sectionMapper.selectOne(new LambdaQueryWrapper<SoftwareHomeSection>()
                .eq(SoftwareHomeSection::getSlotKey, request.getSlotKey().trim())
                .isNull(SoftwareHomeSection::getDeletedAt));
        if (dup != null && (sectionId == null || !dup.getId().equals(sectionId))) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "slotKey 已存在");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SoftwareHomeSection section = sectionId == null ? new SoftwareHomeSection()
                : sectionMapper.selectById(sectionId);
        if (sectionId != null && (section == null || section.getDeletedAt() != null)) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "区块不存在");
        }
        if (sectionId == null) {
            section.setCreatedAt(now);
        }
        section.setSlotKey(request.getSlotKey().trim());
        section.setTitle(request.getTitle().trim());
        section.setSubtitle(trimToNull(request.getSubtitle(), 120));
        section.setKind(kind);
        section.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        section.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        section.setStartAt(request.getStartAt());
        section.setEndAt(request.getEndAt());
        section.setPlatformScope(platforms.isEmpty() ? null : String.join(",", platforms));
        section.setLimitCount(limitCount);
        section.setMinItems(minItems);
        section.setDataSource(source);
        section.setFallbackPolicy(fallback);
        section.setUpdatedAt(now);
        if (sectionId == null) {
            sectionMapper.insert(section);
        } else {
            sectionMapper.updateById(section);
        }

        // MANUAL 条目同事务全量替换
        if ("MANUAL".equals(source)) {
            List<AdminHomeSectionSaveRequest.HomeItemInput> items =
                    request.getItems() == null ? List.of() : request.getItems();
            if (items.size() > MAX_LIMIT) {
                throw new BusinessException("SOFTWARE_HOME_INVALID", "条目最多 " + MAX_LIMIT + " 个");
            }
            Set<String> seen = new HashSet<>();
            int index = 0;
            for (AdminHomeSectionSaveRequest.HomeItemInput item : items) {
                String type = item.getTargetType() == null ? "" : item.getTargetType().trim();
                if (!TARGET_TYPES.contains(type)) {
                    throw new BusinessException("SOFTWARE_HOME_INVALID",
                            "targetType 须为 SOFTWARE/TOPIC/GUIDE/FILTER");
                }
                if ("FILTER".equals(type)) {
                    if (item.getFilterPreset() == null || item.getFilterPreset().isBlank()
                            || item.getFilterPreset().length() > 200) {
                        throw new BusinessException("SOFTWARE_HOME_INVALID",
                                "FILTER 条目须提供 1~200 字筛选 preset");
                    }
                } else if (item.getTargetId() == null) {
                    throw new BusinessException("SOFTWARE_HOME_INVALID",
                            type + " 条目必须提供 targetId");
                }
                if (item.getHeadline() != null && item.getHeadline().length() > 60) {
                    throw new BusinessException("SOFTWARE_HOME_INVALID", "运营标题最长 60 字");
                }
                if (item.getEditorNote() != null && item.getEditorNote().length() > 200) {
                    throw new BusinessException("SOFTWARE_HOME_INVALID", "编辑推荐理由最长 200 字");
                }
                if (!seen.add(type + ":" + (item.getTargetId() == null
                        ? item.getFilterPreset() : item.getTargetId()))) {
                    throw new BusinessException("SOFTWARE_HOME_INVALID", "条目重复");
                }
                index++;
            }
            itemMapper.delete(new LambdaQueryWrapper<SoftwareHomeSectionItem>()
                    .eq(SoftwareHomeSectionItem::getSectionId, section.getId()));
            index = 0;
            for (AdminHomeSectionSaveRequest.HomeItemInput item : items) {
                SoftwareHomeSectionItem entity = new SoftwareHomeSectionItem();
                entity.setSectionId(section.getId());
                entity.setTargetType(item.getTargetType().trim());
                entity.setTargetId(item.getTargetId());
                entity.setFilterPreset(trimToNull(item.getFilterPreset(), 200));
                entity.setHeadline(trimToNull(item.getHeadline(), 60));
                entity.setEditorNote(trimToNull(item.getEditorNote(), 200));
                entity.setCoverAssetId(item.getCoverAssetId());
                entity.setDisplayOrder(item.getDisplayOrder() == null ? index : item.getDisplayOrder());
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                itemMapper.insert(entity);
                index++;
            }
        }
        return section.getId();
    }

    @Override
    @Transactional
    public boolean toggle(Long sectionId, boolean enabled, String idempotencyKey) {
        SoftwareHomeSection section = sectionMapper.selectById(sectionId);
        if (section == null || section.getDeletedAt() != null) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "区块不存在");
        }
        section.setEnabled(enabled);
        section.setUpdatedAt(LocalDateTime.now(clock));
        sectionMapper.updateById(section);
        return true;
    }

    @Override
    @Transactional
    public boolean delete(Long sectionId, String idempotencyKey) {
        SoftwareHomeSection section = sectionMapper.selectById(sectionId);
        if (section == null || section.getDeletedAt() != null) {
            return true; // 幂等
        }
        section.setDeletedAt(LocalDateTime.now(clock));
        section.setEnabled(false);
        section.setUpdatedAt(LocalDateTime.now(clock));
        sectionMapper.updateById(section);
        return true;
    }

    @Override
    @Transactional
    public boolean reorder(List<Long> sectionIds) {
        if (sectionIds == null) {
            throw new BusinessException("SOFTWARE_HOME_INVALID", "排序列表不能为空");
        }
        List<SoftwareHomeSection> existing = sectionMapper.selectList(
                new LambdaQueryWrapper<SoftwareHomeSection>()
                        .isNull(SoftwareHomeSection::getDeletedAt));
        Set<Long> existingIds = new HashSet<>();
        for (SoftwareHomeSection section : existing) {
            existingIds.add(section.getId());
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : sectionIds) {
            if (!existingIds.contains(id)) {
                throw new BusinessException("SOFTWARE_HOME_INVALID", "排序包含未知区块 " + id);
            }
            if (!seen.add(id)) {
                throw new BusinessException("SOFTWARE_HOME_INVALID", "排序包含重复区块 " + id);
            }
        }
        if (seen.size() != existing.size()) {
            throw new BusinessException("SOFTWARE_HOME_INVALID",
                    "排序必须提交完整区块集合（缺 " + (existing.size() - seen.size()) + " 个）");
        }
        int order = 0;
        for (Long id : sectionIds) {
            SoftwareHomeSection section = sectionMapper.selectById(id);
            section.setDisplayOrder(order++);
            section.setUpdatedAt(LocalDateTime.now(clock));
            sectionMapper.updateById(section);
        }
        return true;
    }

    private String trimToNull(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
