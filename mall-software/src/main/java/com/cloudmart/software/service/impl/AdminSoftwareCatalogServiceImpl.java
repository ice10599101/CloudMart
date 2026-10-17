package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminCategorySaveRequest;
import com.cloudmart.software.dto.software.AdminItemQuery;
import com.cloudmart.software.dto.software.AdminTagSaveRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.entity.SoftwareCategory;
import com.cloudmart.software.entity.SoftwareTag;
import com.cloudmart.software.enums.SoftwareLicenseType;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.AdminSoftwareCatalogMapper;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareTagMapper;
import com.cloudmart.software.service.AdminSoftwareCatalogService;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwarePlatformActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 管理端软件目录与分类标签实现（方案 7.5/10.1/10.5）。
 *
 * <p>不变量：目录查询不使用公开可见性谓词（后台可查全部生命周期）；
 * 精选排序 CAS 防覆盖；分类/标签删除均为禁用（物理删除被引用分类会让详情报错）；
 * 写操作全部走 operation(ADMIN) 幂等，变更同事务提交审计。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSoftwareCatalogServiceImpl implements AdminSoftwareCatalogService {

    private static final Set<String> ITEM_STATUSES =
            Set.of("ACTIVE", "PENDING", "FEATURED", "UNLISTED", "BANNED");
    private static final int MAX_FEATURED_RANK = 9999;
    private static final int MAX_REASON_LENGTH = 500;
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?");

    private final AdminSoftwareCatalogMapper catalogMapper;
    private final com.cloudmart.software.feign.UserSupportFeignClient userSupportFeignClient;
    private final SoftwareMapper softwareMapper;
    private final SoftwareCategoryMapper categoryMapper;
    private final SoftwareTagMapper tagMapper;
    private final SoftwareAuditLogMapper auditLogMapper;
    private final SoftwareOperationService operationService;
    private final SoftwarePlatformActionService platformActionService;
    private final Clock clock;

    // ------------------------------------------------------------------
    // 目录查询
    // ------------------------------------------------------------------

    @Override
    public Page<Map<String, Object>> itemPage(AdminItemQuery query) {
        if (query.getStatus() != null && !query.getStatus().isBlank()
                && !ITEM_STATUSES.contains(query.getStatus())) {
            throw new BusinessException("SOFTWARE_QUERY_INVALID",
                    "status 须为 ACTIVE/PENDING/FEATURED/UNLISTED/BANNED");
        }
        if (query.getLicense() != null && !query.getLicense().isBlank()
                && Arrays.stream(SoftwareLicenseType.values())
                        .noneMatch(type -> type.name().equals(query.getLicense()))) {
            throw new BusinessException("SOFTWARE_QUERY_INVALID", "license 非法");
        }
        Page<AdminSoftwareCatalogMapper.ItemRow> rowPage = catalogMapper.pageItems(
                Page.of(query.safePage(), query.safeSize()), query);
        Page<Map<String, Object>> result = new Page<>(rowPage.getCurrent(),
                rowPage.getSize(), rowPage.getTotal());
        List<Map<String, Object>> rows = rowPage.getRecords().stream()
                .map(this::toItemRow).toList();
        fillOwnerNicknames(rows);
        result.setRecords(rows);
        return result;
    }

    /**
     * 目录列表行级补发布者昵称（展示用户名而非数字 ID；同发布资格页先例）。
     * profiles 失败/降级返回空时昵称缺省，前端回落为 ID 展示。
     */
    private void fillOwnerNicknames(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream()
                .map(row -> row.get("ownerId"))
                .filter(java.util.Objects::nonNull)
                .map(value -> {
                    try {
                        return Long.parseLong(String.valueOf(value));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nicknameById = new HashMap<>();
        try {
            for (var profile : userSupportFeignClient.profiles(
                    Map.of("userIds", ids)).data()) {
                nicknameById.put(profile.id(), profile.nickname());
            }
        } catch (Exception ignored) {
            // 资料服务不可用：昵称留空，前端回落 ID 展示（列表主流程不受阻）
        }
        for (Map<String, Object> row : rows) {
            Object raw = row.get("ownerId");
            if (raw == null) {
                continue;
            }
            try {
                String nickname = nicknameById.get(Long.parseLong(String.valueOf(raw)));
                if (nickname != null && !nickname.isBlank()) {
                    row.put("ownerNickname", nickname);
                }
            } catch (NumberFormatException ignored) {
                // 非数字 ownerId：留空
            }
        }
    }

    private Map<String, Object> toItemRow(AdminSoftwareCatalogMapper.ItemRow row) {
        Map<String, Object> item = new HashMap<>();
        item.put("softwareId", String.valueOf(row.id()));
        item.put("ownerId", row.ownerId() == null ? null : String.valueOf(row.ownerId()));
        item.put("name", row.name());
        item.put("summary", row.summary());
        item.put("lifecycle", row.lifecycle());
        item.put("featuredRank", row.featuredRank());
        item.put("lockVersion", row.lockVersion());
        item.put("publicationSeq", row.publicationSeq());
        item.put("publishedRevisionId", row.publishedRevisionId() == null ? null
                : String.valueOf(row.publishedRevisionId()));
        item.put("workingRevisionId", row.workingRevisionId() == null ? null
                : String.valueOf(row.workingRevisionId()));
        item.put("pendingRevisionId", row.pendingRevisionId() == null ? null
                : String.valueOf(row.pendingRevisionId()));
        item.put("categoryId", row.categoryId() == null ? null : String.valueOf(row.categoryId()));
        item.put("categoryName", row.categoryName());
        item.put("licenseType", row.licenseType());
        item.put("commentCount", row.commentCount());
        item.put("favoriteCount", row.favoriteCount());
        item.put("linkAccessCount", row.linkAccessCount());
        item.put("ratingCount", row.ratingCount());
        item.put("ratingSum", row.ratingSum());
        item.put("publishedAt", row.publishedAt() == null ? null : String.valueOf(row.publishedAt()));
        item.put("lastReleaseAt", row.lastReleaseAt() == null ? null
                : String.valueOf(row.lastReleaseAt()));
        item.put("createdAt", row.createdAt() == null ? null : String.valueOf(row.createdAt()));
        item.put("updatedAt", row.updatedAt() == null ? null : String.valueOf(row.updatedAt()));
        return item;
    }

    // ------------------------------------------------------------------
    // 独立封禁/解除（与举报处置 BAN_SOFTWARE 共用 SoftwarePlatformActionService）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public boolean ban(Long adminId, Long softwareId, String idempotencyKey,
                       String reason, Integer expectedVersion) {
        String cleanReason = validateDisposalReason(reason);
        if (expectedVersion == null) {
            throw new BusinessException("SOFTWARE_BAN_INVALID", "缺少 expectedVersion");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_ITEM_BAN,
                idempotencyKey,
                Map.of("softwareId", softwareId, "reason", cleanReason,
                        "expectedVersion", expectedVersion),
                softwareId,
                (Supplier<Void>) () -> {
                    platformActionService.ban(adminId, softwareId, cleanReason, expectedVersion);
                    return null;
                },
                json -> null);
        return true;
    }

    @Override
    @Transactional
    public boolean unban(Long adminId, Long softwareId, String idempotencyKey,
                         String reason, Integer expectedVersion) {
        String cleanReason = validateDisposalReason(reason);
        if (expectedVersion == null) {
            throw new BusinessException("SOFTWARE_BAN_INVALID", "缺少 expectedVersion");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_ITEM_UNBAN,
                idempotencyKey,
                Map.of("softwareId", softwareId, "reason", cleanReason,
                        "expectedVersion", expectedVersion),
                softwareId,
                (Supplier<Void>) () -> {
                    platformActionService.unban(adminId, softwareId, cleanReason, expectedVersion);
                    return null;
                },
                json -> null);
        return true;
    }

    private String validateDisposalReason(String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty() || clean.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("SOFTWARE_BAN_INVALID", "处置原因须为 1~500 字");
        }
        return clean;
    }

    // ------------------------------------------------------------------
    // 精选排序（首页推荐区 P0）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public boolean feature(Long adminId, Long softwareId, String idempotencyKey,
                           Integer featuredRank, Integer expectedVersion) {
        if (featuredRank == null || featuredRank < 0 || featuredRank > MAX_FEATURED_RANK) {
            throw new BusinessException("SOFTWARE_FEATURE_INVALID",
                    "featuredRank 须为 0~" + MAX_FEATURED_RANK + "（0=取消精选）");
        }
        if (expectedVersion == null) {
            throw new BusinessException("SOFTWARE_FEATURE_INVALID", "缺少 expectedVersion");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_ITEM_FEATURE,
                idempotencyKey,
                Map.of("softwareId", softwareId, "featuredRank", featuredRank,
                        "expectedVersion", expectedVersion),
                softwareId,
                (Supplier<Void>) () -> {
                    doFeature(adminId, softwareId, featuredRank, expectedVersion);
                    return null;
                },
                json -> null);
        return true;
    }

    private void doFeature(Long adminId, Long softwareId, Integer featuredRank,
                           Integer expectedVersion) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getLifecycle() == SoftwareLifecycle.DELETED) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (!expectedVersion.equals(software.getLockVersion())) {
            throw new BusinessException("SOFTWARE_VERSION_CONFLICT",
                    "软件已被其他操作更新，请刷新后重试");
        }
        if (featuredRank.equals(software.getFeaturedRank())) {
            return; // 幂等：目标排序不变不写审计
        }
        String from = String.valueOf(software.getFeaturedRank());
        software.setFeaturedRank(featuredRank);
        software.setLockVersion(software.getLockVersion() + 1);
        softwareMapper.updateById(software);
        writeAudit(softwareId, adminId, "FEATURE", from, String.valueOf(featuredRank),
                featuredRank == 0 ? "取消精选" : "设置精选排序");
    }

    // ------------------------------------------------------------------
    // 分类维护（运营视图含禁用）
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<SoftwareCategory>()
                        .orderByAsc(SoftwareCategory::getSortOrder)
                        .orderByAsc(SoftwareCategory::getId))
                .stream().map(category -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("categoryId", String.valueOf(category.getId()));
                    row.put("name", category.getName());
                    row.put("slug", category.getSlug());
                    row.put("icon", category.getIcon());
                    row.put("sortOrder", category.getSortOrder());
                    row.put("enabled", category.getEnabled());
                    row.put("updatedAt", String.valueOf(category.getUpdatedAt()));
                    return row;
                }).toList();
    }

    @Override
    @Transactional
    public Long saveCategory(Long adminId, Long categoryId, String idempotencyKey,
                             AdminCategorySaveRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty() || name.length() > 40) {
            throw new BusinessException("SOFTWARE_CATEGORY_INVALID", "分类名须为 1~40 字");
        }
        String slug = request.getSlug() == null ? "" : request.getSlug().trim();
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new BusinessException("SOFTWARE_CATEGORY_INVALID",
                    "slug 须为小写字母/数字/连字符（1~64 字符，不以连字符开头结尾）");
        }
        String icon = request.getIcon() == null ? null : request.getIcon().trim();
        if (icon != null && (icon.isEmpty() || icon.length() > 100)) {
            throw new BusinessException("SOFTWARE_CATEGORY_INVALID", "图标标识须为 1~100 字符");
        }
        int sortOrder = request.getSortOrder() == null ? 0 : request.getSortOrder();
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        return operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_CATEGORY_SAVE,
                idempotencyKey,
                Map.of("categoryId", String.valueOf(categoryId), "name", name, "slug", slug,
                        "sortOrder", sortOrder, "enabled", enabled),
                null,
                () -> doSaveCategory(categoryId, name, slug, icon, sortOrder, enabled),
                Long::valueOf);
    }

    private Long doSaveCategory(Long categoryId, String name, String slug, String icon,
                                int sortOrder, boolean enabled) {
        Long conflictId = categoryMapper.selectList(new LambdaQueryWrapper<SoftwareCategory>()
                        .eq(SoftwareCategory::getSlug, slug))
                .stream().map(SoftwareCategory::getId)
                .filter(id -> !id.equals(categoryId))
                .findFirst().orElse(null);
        if (conflictId != null) {
            throw new BusinessException("SOFTWARE_CATEGORY_SLUG_EXISTS", "分类标识已存在");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (categoryId == null) {
            SoftwareCategory category = new SoftwareCategory();
            category.setName(name);
            category.setSlug(slug);
            category.setIcon(icon);
            category.setSortOrder(sortOrder);
            category.setEnabled(enabled);
            category.setCreatedAt(now);
            category.setUpdatedAt(now);
            categoryMapper.insert(category);
            return category.getId();
        }
        SoftwareCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("SOFTWARE_CATEGORY_NOT_FOUND", "分类不存在");
        }
        category.setName(name);
        category.setSlug(slug);
        category.setIcon(icon);
        category.setSortOrder(sortOrder);
        category.setEnabled(enabled);
        category.setUpdatedAt(now);
        categoryMapper.updateById(category);
        return category.getId();
    }

    @Override
    @Transactional
    public boolean disableCategory(Long adminId, Long categoryId, String idempotencyKey) {
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_CATEGORY_DISABLE,
                idempotencyKey, Map.of("categoryId", String.valueOf(categoryId)), null,
                (Supplier<Void>) () -> {
                    SoftwareCategory category = categoryMapper.selectById(categoryId);
                    if (category == null) {
                        throw new BusinessException("SOFTWARE_CATEGORY_NOT_FOUND", "分类不存在");
                    }
                    // 禁用而非删除：被引用分类物理删除会让历史详情/筛选报错（10.5）
                    if (Boolean.TRUE.equals(category.getEnabled())) {
                        category.setEnabled(false);
                        category.setUpdatedAt(LocalDateTime.now(clock));
                        categoryMapper.updateById(category);
                    }
                    return null;
                },
                json -> null);
        return true;
    }

    // ------------------------------------------------------------------
    // 标签维护（运营视图含禁用）
    // ------------------------------------------------------------------

    @Override
    public Page<Map<String, Object>> tags(String keyword, Integer page, Integer size) {
        Page<SoftwareTag> tagPage = tagMapper.selectPage(
                Page.of(page == null || page < 1 ? 1 : Math.min(page, 1000),
                        size == null || size < 1 ? 20 : Math.min(size, 100)),
                new LambdaQueryWrapper<SoftwareTag>()
                        .like(keyword != null && !keyword.isBlank(), SoftwareTag::getName, keyword)
                        .orderByAsc(SoftwareTag::getId));
        Page<Map<String, Object>> result = new Page<>(tagPage.getCurrent(), tagPage.getSize(),
                tagPage.getTotal());
        result.setRecords(tagPage.getRecords().stream().map(tag -> {
            Map<String, Object> row = new HashMap<>();
            row.put("tagId", String.valueOf(tag.getId()));
            row.put("name", tag.getName());
            row.put("enabled", tag.getEnabled());
            row.put("updatedAt", String.valueOf(tag.getUpdatedAt()));
            return row;
        }).toList());
        return result;
    }

    @Override
    @Transactional
    public Long saveTag(Long adminId, Long tagId, String idempotencyKey,
                        AdminTagSaveRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.length() < 2 || name.length() > 20) {
            throw new BusinessException("SOFTWARE_TAG_INVALID", "标签名须为 2~20 字");
        }
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        return operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_TAG_SAVE,
                idempotencyKey,
                Map.of("tagId", String.valueOf(tagId), "name", name, "enabled", enabled),
                null,
                () -> doSaveTag(tagId, name, enabled),
                Long::valueOf);
    }

    private Long doSaveTag(Long tagId, String name, boolean enabled) {
        String normalized = normalizeTag(name);
        if (normalized.length() > 40) {
            throw new BusinessException("SOFTWARE_TAG_INVALID", "标签名过长");
        }
        Long conflictId = tagMapper.selectList(new LambdaQueryWrapper<SoftwareTag>()
                        .eq(SoftwareTag::getNormalizedName, normalized))
                .stream().map(SoftwareTag::getId)
                .filter(id -> !id.equals(tagId))
                .findFirst().orElse(null);
        if (conflictId != null) {
            throw new BusinessException("SOFTWARE_TAG_NAME_EXISTS", "标签已存在（规范化后重复）");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (tagId == null) {
            SoftwareTag tag = new SoftwareTag();
            tag.setName(name);
            tag.setNormalizedName(normalized);
            tag.setEnabled(enabled);
            tag.setCreatedAt(now);
            tag.setUpdatedAt(now);
            tagMapper.insert(tag);
            return tag.getId();
        }
        SoftwareTag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("SOFTWARE_TAG_NOT_FOUND", "标签不存在");
        }
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setEnabled(enabled);
        tag.setUpdatedAt(now);
        tagMapper.updateById(tag);
        return tag.getId();
    }

    @Override
    @Transactional
    public boolean disableTag(Long adminId, Long tagId, String idempotencyKey) {
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_TAG_DISABLE,
                idempotencyKey, Map.of("tagId", String.valueOf(tagId)), null,
                (Supplier<Void>) () -> {
                    SoftwareTag tag = tagMapper.selectById(tagId);
                    if (tag == null) {
                        throw new BusinessException("SOFTWARE_TAG_NOT_FOUND", "标签不存在");
                    }
                    // 禁用而非删除：作者不再能选择，历史快照仍保留标签名（10.5）
                    if (Boolean.TRUE.equals(tag.getEnabled())) {
                        tag.setEnabled(false);
                        tag.setUpdatedAt(LocalDateTime.now(clock));
                        tagMapper.updateById(tag);
                    }
                    return null;
                },
                json -> null);
        return true;
    }

    /** 规范化去重键：去首尾空白 + 小写 */
    private String normalizeTag(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private void writeAudit(Long softwareId, Long adminId, String action,
                            String from, String to, String reason) {
        SoftwareAuditLog logEntry = new SoftwareAuditLog();
        logEntry.setSoftwareId(softwareId);
        logEntry.setActorId(adminId);
        logEntry.setActorType("ADMIN");
        logEntry.setAction(action);
        logEntry.setFromStatus(from);
        logEntry.setToStatus(to);
        logEntry.setReason(reason);
        logEntry.setCreatedAt(LocalDateTime.now(clock));
        auditLogMapper.insert(logEntry);
    }
}
