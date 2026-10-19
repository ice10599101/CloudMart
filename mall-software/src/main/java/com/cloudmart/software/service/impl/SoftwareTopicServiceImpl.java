package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareTopic;
import com.cloudmart.software.entity.SoftwareTopicItem;
import com.cloudmart.software.repository.SoftwareTopicItemMapper;
import com.cloudmart.software.repository.SoftwareTopicMapper;
import com.cloudmart.software.service.SoftwareQueryService;
import com.cloudmart.software.service.SoftwareTopicService;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 专题公开读取实现（方案 6.11，P1）：PUBLISHED 才可见；清单经 cardsByIds
 * 实时执行公开可见谓词（下架软件自动从公共专题过滤）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareTopicServiceImpl implements SoftwareTopicService {

    private final SoftwareTopicMapper topicMapper;
    private final SoftwareTopicItemMapper topicItemMapper;
    private final SoftwareQueryService queryService;

    @Override
    public List<Map<String, Object>> listPublished() {
        // SF05：只返回生效专题——PUBLISHED 且在投放时间窗内（NULL 边界=不限制）
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.Clock.systemUTC());
        return topicMapper.selectList(new LambdaQueryWrapper<SoftwareTopic>()
                        .eq(SoftwareTopic::getStatus, "PUBLISHED")
                        .and(w -> w.isNull(SoftwareTopic::getScheduledStartAt)
                                .le(SoftwareTopic::getScheduledStartAt, now))
                        .and(w -> w.isNull(SoftwareTopic::getScheduledEndAt)
                                .gt(SoftwareTopic::getScheduledEndAt, now))
                        .orderByAsc(SoftwareTopic::getSortOrder)
                        .orderByAsc(SoftwareTopic::getId))
                .stream().map(topic -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("topicId", String.valueOf(topic.getId()));
                    row.put("slug", topic.getSlug());
                    row.put("title", topic.getTitle());
                    row.put("summary", topic.getSummary());
                    row.put("coverAssetId", topic.getCoverAssetId() == null ? null
                            : String.valueOf(topic.getCoverAssetId()));
                    row.put("sortOrder", topic.getSortOrder());
                    return row;
                }).toList();
    }

    @Override
    public Map<String, Object> publishedDetail(String slug) {
        SoftwareTopic topic = slug == null ? null : topicMapper.selectOne(
                new LambdaQueryWrapper<SoftwareTopic>().eq(SoftwareTopic::getSlug, slug));
        // 先判存在性再读时间窗（D44 部署实测：slug 不存在时在 scheduledVisible 处 NPE）
        if (topic == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "专题不存在或已下线");
        }
        // SF05：下线/未到投放时间的专题按"不存在"处理，不泄露未生效条目
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.Clock.systemUTC());
        boolean scheduledVisible = (topic.getScheduledStartAt() == null
                || !topic.getScheduledStartAt().isAfter(now))
                && (topic.getScheduledEndAt() == null || topic.getScheduledEndAt().isAfter(now));
        if (!"PUBLISHED".equals(topic.getStatus()) || !scheduledVisible) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "专题不存在或已下线");
        }
        List<SoftwareTopicItem> items = topicItemMapper.selectList(
                new LambdaQueryWrapper<SoftwareTopicItem>()
                        .eq(SoftwareTopicItem::getTopicId, topic.getId())
                        .orderByAsc(SoftwareTopicItem::getSortOrder)
                        .orderByAsc(SoftwareTopicItem::getId));
        List<Long> orderedIds = items.stream().map(SoftwareTopicItem::getSoftwareId).toList();
        Map<Long, SoftwareCardVO> cards = queryService.cardsByIds(orderedIds).stream()
                .collect(Collectors.toMap(SoftwareCardVO::id, Function.identity()));
        List<SoftwareCardVO> visibleItems = orderedIds.stream()
                .map(cards::get)
                .filter(Objects::nonNull)
                .toList();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("topicId", String.valueOf(topic.getId()));
        detail.put("slug", topic.getSlug());
        detail.put("title", topic.getTitle());
        detail.put("summary", topic.getSummary());
        detail.put("description", topic.getDescription());
        detail.put("coverAssetId", topic.getCoverAssetId() == null ? null
                : String.valueOf(topic.getCoverAssetId()));
        // UX02：工具箱三要素
        detail.put("audience", topic.getAudience());
        detail.put("problem", topic.getProblem());
        detail.put("prerequisites", topic.getPrerequisites());
        detail.put("items", visibleItems);
        // UX02：分组结构（稳定 groupKey 关联；受限软件仅公开信息，不泄露未生效条目）
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (SoftwareTopicItem item : items) {
            SoftwareCardVO card = cards.get(item.getSoftwareId());
            if (card == null) {
                continue; // 不可见软件整项隐藏（保留分组键结构）
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("softwareId", String.valueOf(item.getSoftwareId()));
            entry.put("groupKey", item.getGroupKey() == null ? "" : item.getGroupKey());
            entry.put("roleNote", item.getRoleNote());
            entry.put("optional", Boolean.TRUE.equals(item.getOptional()));
            entry.put("sortOrder", item.getSortOrder());
            groups.computeIfAbsent(item.getGroupKey() == null ? "" : item.getGroupKey(),
                    k -> new java.util.ArrayList<>()).add(entry);
        }
        List<Map<String, Object>> groupList = new java.util.ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("groupKey", entry.getKey());
            group.put("items", entry.getValue());
            groupList.add(group);
        }
        detail.put("groups", groupList);
        return detail;
    }
}
