package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminTopicSaveRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareTopic;
import com.cloudmart.software.entity.SoftwareTopicItem;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareTopicItemMapper;
import com.cloudmart.software.repository.SoftwareTopicMapper;
import com.cloudmart.software.service.AdminSoftwareTopicService;
import com.cloudmart.software.service.SoftwareOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 管理端专题维护实现（方案 6.11/10.6，P1）。
 *
 * <p>不变量：清单全量替换（不做增量拼接，排序项整体维护）；引用的软件必须存在
 * （软删除/不存在的软件拒绝加入）；删除为禁用；写操作走 operation(ADMIN) 幂等。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSoftwareTopicServiceImpl implements AdminSoftwareTopicService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");
    private static final int MAX_ITEMS = 100;
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?");

    private final SoftwareTopicMapper topicMapper;
    private final SoftwareTopicItemMapper topicItemMapper;
    private final SoftwareMapper softwareMapper;
    private final SoftwareOperationService operationService;
    private final Clock clock;

    @Override
    public Page<Map<String, Object>> page(int page, int size) {
        Page<SoftwareTopic> topicPage = topicMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwareTopic>()
                        .orderByAsc(SoftwareTopic::getSortOrder)
                        .orderByAsc(SoftwareTopic::getId));
        Page<Map<String, Object>> result = new Page<>(topicPage.getCurrent(), topicPage.getSize(),
                topicPage.getTotal());
        result.setRecords(topicPage.getRecords().stream().map(topic -> {
            Map<String, Object> row = new HashMap<>();
            row.put("topicId", String.valueOf(topic.getId()));
            row.put("slug", topic.getSlug());
            row.put("title", topic.getTitle());
            row.put("status", topic.getStatus());
            row.put("sortOrder", topic.getSortOrder());
            row.put("itemCount", topicItemMapper.selectCount(
                    new LambdaQueryWrapper<SoftwareTopicItem>()
                            .eq(SoftwareTopicItem::getTopicId, topic.getId())));
            row.put("updatedAt", String.valueOf(topic.getUpdatedAt()));
            return row;
        }).toList());
        return result;
    }

    @Override
    public Map<String, Object> detail(Long topicId) {
        SoftwareTopic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "专题不存在");
        }
        List<Map<String, Object>> items = topicItemMapper.selectList(
                        new LambdaQueryWrapper<SoftwareTopicItem>()
                                .eq(SoftwareTopicItem::getTopicId, topicId)
                                .orderByAsc(SoftwareTopicItem::getSortOrder)
                                .orderByAsc(SoftwareTopicItem::getId))
                .stream().map(item -> Map.<String, Object>of(
                        "softwareId", String.valueOf(item.getSoftwareId()),
                        "sortOrder", item.getSortOrder()))
                .toList();
        Map<String, Object> detail = new HashMap<>();
        detail.put("topicId", String.valueOf(topic.getId()));
        detail.put("slug", topic.getSlug());
        detail.put("title", topic.getTitle());
        detail.put("summary", topic.getSummary());
        detail.put("coverAssetId", topic.getCoverAssetId() == null ? null
                : String.valueOf(topic.getCoverAssetId()));
        detail.put("status", topic.getStatus());
        detail.put("sortOrder", topic.getSortOrder());
        detail.put("items", items);
        return detail;
    }

    @Override
    @Transactional
    public Long save(Long adminId, Long topicId, String idempotencyKey,
                     AdminTopicSaveRequest request) {
        String slug = request.getSlug() == null ? "" : request.getSlug().trim();
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new BusinessException("SOFTWARE_TOPIC_INVALID",
                    "slug 须为小写字母/数字/连字符（1~64 字符）");
        }
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty() || title.length() > 80) {
            throw new BusinessException("SOFTWARE_TOPIC_INVALID", "标题须为 1~80 字");
        }
        String summary = request.getSummary() == null ? null : request.getSummary().trim();
        if (summary != null && summary.length() > 500) {
            throw new BusinessException("SOFTWARE_TOPIC_INVALID", "运营说明不能超过 500 字");
        }
        String status = request.getStatus() == null ? "DRAFT" : request.getStatus();
        if (!STATUSES.contains(status)) {
            throw new BusinessException("SOFTWARE_TOPIC_INVALID",
                    "status 须为 DRAFT/PUBLISHED/DISABLED");
        }
        List<AdminTopicSaveRequest.Item> items = request.getItems() == null ? List.of()
                : request.getItems();
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException("SOFTWARE_TOPIC_INVALID", "专题软件最多 100 个");
        }
        List<AdminTopicSaveRequest.Item> cleanItems = new ArrayList<>();
        for (AdminTopicSaveRequest.Item item : items) {
            if (item == null || item.getSoftwareId() == null) {
                throw new BusinessException("SOFTWARE_TOPIC_INVALID", "清单项缺少 softwareId");
            }
            Software software = softwareMapper.selectById(item.getSoftwareId());
            if (software == null || software.getDeletedAt() != null) {
                throw new BusinessException("SOFTWARE_TOPIC_INVALID",
                        "软件不存在或已删除：" + item.getSoftwareId());
            }
            cleanItems.add(item);
        }
        Long resolvedTopicId = topicId;
        return operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_TOPIC_SAVE,
                idempotencyKey,
                Map.of("topicId", String.valueOf(resolvedTopicId), "slug", slug, "title", title,
                        "status", status, "itemCount", cleanItems.size()),
                null,
                () -> doSave(resolvedTopicId, slug, title, summary, request.getCoverAssetId(),
                        status, request.getSortOrder() == null ? 0 : request.getSortOrder(),
                        cleanItems),
                Long::valueOf);
    }

    private Long doSave(Long topicId, String slug, String title, String summary,
                        Long coverAssetId, String status, int sortOrder,
                        List<AdminTopicSaveRequest.Item> items) {
        // slug 唯一（排除自身）
        SoftwareTopic conflict = topicMapper.selectOne(new LambdaQueryWrapper<SoftwareTopic>()
                .eq(SoftwareTopic::getSlug, slug));
        if (conflict != null && !conflict.getId().equals(topicId)) {
            throw new BusinessException("SOFTWARE_TOPIC_SLUG_EXISTS", "专题标识已存在");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        SoftwareTopic topic;
        if (topicId == null) {
            topic = new SoftwareTopic();
            topic.setSlug(slug);
            topic.setTitle(title);
            topic.setSummary(summary);
            topic.setCoverAssetId(coverAssetId);
            topic.setStatus(status);
            topic.setSortOrder(sortOrder);
            topic.setCreatedAt(now);
            topic.setUpdatedAt(now);
            topicMapper.insert(topic);
        } else {
            topic = topicMapper.selectById(topicId);
            if (topic == null) {
                throw new BusinessException("SOFTWARE_NOT_FOUND", "专题不存在");
            }
            topic.setSlug(slug);
            topic.setTitle(title);
            topic.setSummary(summary);
            topic.setCoverAssetId(coverAssetId);
            topic.setStatus(status);
            topic.setSortOrder(sortOrder);
            topic.setUpdatedAt(now);
            topicMapper.updateById(topic);
        }
        // 清单全量替换（整体维护语义）
        topicItemMapper.delete(new LambdaQueryWrapper<SoftwareTopicItem>()
                .eq(SoftwareTopicItem::getTopicId, topic.getId()));
        int index = 0;
        for (AdminTopicSaveRequest.Item item : items) {
            SoftwareTopicItem entity = new SoftwareTopicItem();
            entity.setTopicId(topic.getId());
            entity.setSoftwareId(item.getSoftwareId());
            entity.setSortOrder(item.getSortOrder() == null ? index : item.getSortOrder());
            topicItemMapper.insert(entity);
            index++;
        }
        return topic.getId();
    }

    @Override
    @Transactional
    public boolean disable(Long adminId, Long topicId, String idempotencyKey) {
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_TOPIC_DISABLE,
                idempotencyKey, Map.of("topicId", String.valueOf(topicId)), null,
                (Supplier<Void>) () -> {
                    SoftwareTopic topic = topicMapper.selectById(topicId);
                    if (topic == null) {
                        throw new BusinessException("SOFTWARE_NOT_FOUND", "专题不存在");
                    }
                    if (!"DISABLED".equals(topic.getStatus())) {
                        topic.setStatus("DISABLED");
                        topic.setUpdatedAt(LocalDateTime.now(clock));
                        topicMapper.updateById(topic);
                    }
                    return null;
                },
                json -> null);
        return true;
    }
}
