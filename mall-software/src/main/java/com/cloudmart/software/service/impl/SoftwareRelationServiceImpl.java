package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminRelationSaveRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRelation;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRelationMapper;
import com.cloudmart.software.service.SoftwareRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 软件同类关系（UX03）实现：全量替换 + 严格校验。
 * 校验：≤4 条（详情展示上限）、禁自关联、去重（软件+类型）、
 * 目标必须存在且已发布（ACTIVE），类型 SIMILAR_USE/ALTERNATIVE_PLATFORM，
 * reason ≤200 字。公开聚合的可见性过滤在查询侧实时执行。
 */
@Service
@RequiredArgsConstructor
public class SoftwareRelationServiceImpl implements SoftwareRelationService {

    private static final int MAX_ITEMS = 4;
    private static final Set<String> TYPES = Set.of("SIMILAR_USE", "ALTERNATIVE_PLATFORM");

    private final SoftwareRelationMapper relationMapper;
    private final SoftwareMapper softwareMapper;

    @Override
    public List<Map<String, Object>> listRelations(Long softwareId) {
        return relationMapper.selectList(new LambdaQueryWrapper<SoftwareRelation>()
                        .eq(SoftwareRelation::getSoftwareId, softwareId)
                        .orderByAsc(SoftwareRelation::getDisplayOrder)
                        .orderByAsc(SoftwareRelation::getId))
                .stream()
                .map(r -> Map.<String, Object>of(
                        "relatedId", r.getRelatedId(),
                        "relationType", r.getRelationType(),
                        "reason", r.getReason() == null ? "" : r.getReason(),
                        "displayOrder", r.getDisplayOrder()))
                .toList();
    }

    @Override
    @Transactional
    public int saveRelations(Long softwareId, AdminRelationSaveRequest request) {
        List<AdminRelationSaveRequest.Item> items =
                request == null || request.getItems() == null ? List.of() : request.getItems();
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException("SOFTWARE_RELATION_INVALID", "同类关系最多 " + MAX_ITEMS + " 条");
        }
        Set<String> seen = new HashSet<>();
        for (AdminRelationSaveRequest.Item item : items) {
            if (item.getRelatedId() == null) {
                throw new BusinessException("SOFTWARE_RELATION_INVALID", "关联软件不能为空");
            }
            if (item.getRelatedId().equals(softwareId)) {
                throw new BusinessException("SOFTWARE_RELATION_INVALID", "不能关联软件自身");
            }
            String type = item.getRelationType() == null ? "" : item.getRelationType().trim();
            if (!TYPES.contains(type)) {
                throw new BusinessException("SOFTWARE_RELATION_INVALID",
                        "relationType 须为 SIMILAR_USE/ALTERNATIVE_PLATFORM");
            }
            if (!seen.add(item.getRelatedId() + ":" + type)) {
                throw new BusinessException("SOFTWARE_RELATION_INVALID",
                        "同一软件同一关系类型重复（relatedId=" + item.getRelatedId() + "）");
            }
            if (item.getReason() != null && item.getReason().length() > 200) {
                throw new BusinessException("SOFTWARE_RELATION_INVALID", "关系说明最长 200 字");
            }
            // 目标必须存在且已发布（未发布/已删除目标不允许配置）
            Software target = softwareMapper.selectById(item.getRelatedId());
            if (target == null || target.getPublishedRevisionId() == null
                    || target.getLifecycle() == SoftwareLifecycle.DELETED) {
                throw new BusinessException("SOFTWARE_RELATION_INVALID",
                        "关联目标不存在或未发布（relatedId=" + item.getRelatedId() + "）");
            }
        }

        relationMapper.delete(new LambdaQueryWrapper<SoftwareRelation>()
                .eq(SoftwareRelation::getSoftwareId, softwareId));
        int index = 0;
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());
        for (AdminRelationSaveRequest.Item item : items) {
            SoftwareRelation relation = new SoftwareRelation();
            relation.setSoftwareId(softwareId);
            relation.setRelatedId(item.getRelatedId());
            relation.setRelationType(item.getRelationType().trim());
            relation.setReason(item.getReason() == null ? null : item.getReason().trim());
            relation.setDisplayOrder(item.getDisplayOrder() == null ? index : item.getDisplayOrder());
            relation.setCreatedAt(now);
            relation.setUpdatedAt(now);
            relationMapper.insert(relation);
            index++;
        }
        return items.size();
    }
}
