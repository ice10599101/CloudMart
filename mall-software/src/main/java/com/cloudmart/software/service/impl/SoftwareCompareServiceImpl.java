package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareDeclared;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.SoftwareDeclaredMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.service.SoftwareCompareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UX04-B 实现：对比数据每次读取重校验可见性；declared 缺失返回 null=未提供；
 * 首批最多 3 款并排，不生成带立场的排行结论。
 */
@Service
@RequiredArgsConstructor
public class SoftwareCompareServiceImpl implements SoftwareCompareService {

    private static final int MIN_ITEMS = 2;
    private static final int MAX_ITEMS = 3;

    private final SoftwareMapper softwareMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final com.cloudmart.software.repository.SoftwareReleaseMapper releaseMapper;
    private final SoftwareDeclaredMapper declaredMapper;
    private final Clock clock;

    @Override
    public List<Map<String, Object>> compare(String ids) {
        List<Long> idList = parseIds(ids);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long id : idList) {
            Software software = softwareMapper.selectById(id);
            // 每次读取重校验可见性：非公开目标统一"软件不存在"（不泄露下架/封禁）
            if (software == null || software.getPublishedRevisionId() == null
                    || software.getLifecycle() != SoftwareLifecycle.ACTIVE) {
                throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
            }
            items.add(compareItem(software));
        }
        return items;
    }

    private Map<String, Object> compareItem(Software software) {
        SoftwareRevision revision = revisionMapper.selectById(software.getPublishedRevisionId());
        SoftwareDeclared declared = declaredMapper.selectById(software.getId());
        // 平台集：revision 的版本来源覆盖去重（source 挂在 release 下）
        Set<String> platforms = new LinkedHashSet<>();
        var releases = releaseMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareRelease>()
                        .eq(com.cloudmart.software.entity.SoftwareRelease::getRevisionId, revision.getId()));
        List<Long> releaseIds = releases.stream()
                .map(com.cloudmart.software.entity.SoftwareRelease::getId).toList();
        if (!releaseIds.isEmpty()) {
            for (SoftwareSource source : sourceMapper.selectList(
                    new LambdaQueryWrapper<SoftwareSource>()
                            .in(SoftwareSource::getReleaseId, releaseIds))) {
                if (source.getPlatform() != null) {
                    platforms.add(source.getPlatform().name());
                }
            }
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("softwareId", String.valueOf(software.getId()));
        item.put("name", revision.getName());
        item.put("summary", revision.getSummary()); // 用途
        item.put("iconAssetId", revision.getIconAssetId() == null
                ? null : String.valueOf(revision.getIconAssetId()));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("platforms", List.copyOf(platforms));
        fields.put("licenseType", revision.getLicenseType() == null
                ? null : revision.getLicenseType().name()); // 已知许可类别（不含实时价格）
        fields.put("openSource", declared == null ? null : declared.getOpenSource());
        fields.put("offlineCapable", declared == null ? null : declared.getOfflineCapable());
        fields.put("languages", parseLanguages(revision.getLanguages()));
        fields.put("systemRequirements", revision.getSystemRequirements());
        item.put("lastReleaseAt", software.getLastReleaseAt() == null
                ? null : software.getLastReleaseAt().toString());
        item.put("fields", fields);
        int ratingCount = software.getRatingCount() == null ? 0 : software.getRatingCount();
        item.put("ratingCount", ratingCount);
        item.put("ratingAvg", ratingCount == 0 || software.getRatingSum() == null ? null
                : Math.round(software.getRatingSum() * 10.0 / ratingCount) / 10.0);
        item.put("accessMode", revision.getAccessMode() == null
                ? null : revision.getAccessMode().name()); // 获取条件
        return item;
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            throw new BusinessException("SOFTWARE_COMPARE_INVALID", "ids 不能为空");
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (String part : ids.split(",")) {
            try {
                distinct.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                throw new BusinessException("SOFTWARE_COMPARE_INVALID", "存在非法 ID");
            }
        }
        if (distinct.size() < MIN_ITEMS || distinct.size() > MAX_ITEMS) {
            throw new BusinessException("SOFTWARE_COMPARE_INVALID",
                    "对比须为 " + MIN_ITEMS + "~" + MAX_ITEMS + " 个去重软件");
        }
        return List.copyOf(distinct);
    }

    private List<String> parseLanguages(String languagesJson) {
        if (languagesJson == null || languagesJson.isBlank()) {
            return List.of();
        }
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(
                    languagesJson, new tools.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Map<String, Object> declaredOf(Long softwareId) {
        SoftwareDeclared declared = declaredMapper.selectById(softwareId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("softwareId", String.valueOf(softwareId));
        view.put("openSource", declared == null ? null : declared.getOpenSource());
        view.put("offlineCapable", declared == null ? null : declared.getOfflineCapable());
        view.put("declaredNote", declared == null ? null : declared.getDeclaredNote());
        view.put("updatedAt", declared == null ? null
                : declared.getUpdatedAt() == null ? null : declared.getUpdatedAt().toString());
        return view;
    }

    @Override
    @Transactional
    public void saveDeclared(Long softwareId, Boolean openSource, Boolean offlineCapable,
                             String note, Long adminUserId, String idempotencyKey) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || "DELETED".equalsIgnoreCase(
                software.getLifecycle() == null ? "" : software.getLifecycle().name())) {
            throw new BusinessException("SOFTWARE_DECLARED_INVALID", "软件不存在");
        }
        if (note != null && note.length() > 200) {
            throw new BusinessException("SOFTWARE_DECLARED_INVALID", "声明说明最长 200 字");
        }
        SoftwareDeclared existing = declaredMapper.selectById(softwareId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (existing == null) {
            existing = new SoftwareDeclared();
            existing.setSoftwareId(softwareId);
        }
        existing.setOpenSource(openSource);
        existing.setOfflineCapable(offlineCapable);
        existing.setDeclaredNote(note == null || note.isBlank() ? null : note.trim());
        existing.setUpdatedBy(adminUserId);
        existing.setUpdatedAt(now);
        if (declaredMapper.selectById(softwareId) == null) {
            declaredMapper.insert(existing);
        } else {
            declaredMapper.updateById(existing);
        }
    }
}
