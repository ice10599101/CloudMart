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
        return topicMapper.selectList(new LambdaQueryWrapper<SoftwareTopic>()
                        .eq(SoftwareTopic::getStatus, "PUBLISHED")
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
        if (topic == null || !"PUBLISHED".equals(topic.getStatus())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "专题不存在");
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
        detail.put("coverAssetId", topic.getCoverAssetId() == null ? null
                : String.valueOf(topic.getCoverAssetId()));
        detail.put("items", visibleItems);
        return detail;
    }
}
